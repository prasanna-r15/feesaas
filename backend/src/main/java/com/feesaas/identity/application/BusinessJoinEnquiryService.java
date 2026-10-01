package com.feesaas.identity.application;

import com.feesaas.auth.infra.AuthUserRepository;
import com.feesaas.auth.infra.IdentityRepository;
import com.feesaas.config.application.PlatformConfigService;
import com.feesaas.identity.infra.BusinessJoinEnquiryRepository;
import com.feesaas.notify.application.PlatformSmtpMailer;
import com.feesaas.notify.infra.PlatformMailSettingsRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.shared.text.ConfigTemplates;
import com.feesaas.tenant.application.TenantService;
import com.feesaas.tenant.application.TenantService.TenantView;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessJoinEnquiryService {

    private static final Logger log = LoggerFactory.getLogger(BusinessJoinEnquiryService.class);
    private static final String DEFAULT_SUBJECT = "Join-business enquiry · {{businessName}}";
    private static final String DEFAULT_BODY =
            "<p>A DueMate individual asked to open their own business.</p>"
                    + "<p><b>Name:</b> {{name}}<br/><b>Email:</b> {{email}}<br/><b>Phone:</b> {{phone}}"
                    + "<br/><b>Business:</b> {{businessName}}<br/><b>City:</b> {{city}}</p><p>{{message}}</p>";

    private final BusinessJoinEnquiryRepository enquiries;
    private final IdentityRepository identity;
    private final PlatformMailSettingsRepository mailSettings;
    private final PlatformSmtpMailer mailer;
    private final PlatformConfigService configs;
    private final SupportChatService support;
    private final AuthUserRepository users;
    private final TenantExecutor tenants;
    private final TenantService tenantService;

    public BusinessJoinEnquiryService(
            BusinessJoinEnquiryRepository enquiries,
            IdentityRepository identity,
            PlatformMailSettingsRepository mailSettings,
            PlatformSmtpMailer mailer,
            PlatformConfigService configs,
            SupportChatService support,
            AuthUserRepository users,
            TenantExecutor tenants,
            TenantService tenantService) {
        this.enquiries = enquiries;
        this.identity = identity;
        this.mailSettings = mailSettings;
        this.mailer = mailer;
        this.configs = configs;
        this.support = support;
        this.users = users;
        this.tenants = tenants;
        this.tenantService = tenantService;
    }

    @Transactional
    public EnquiryView submit(String businessName, String city, String message) {
        UUID userId = CurrentUser.id();
        IdentityRepository.Contact user = identity.contact(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in again."));
        String biz = businessName == null ? "" : businessName.trim();
        if (biz.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Tell us the gym or business name.");
        }
        UUID id = enquiries.insert(
                userId,
                user.fullName(),
                user.email(),
                user.phone(),
                biz,
                blank(city),
                blank(message));
        try {
            String chat = "Business enquiry: " + biz
                    + (blank(city) == null ? "" : " · " + city.trim())
                    + (blank(message) == null ? "" : "\n" + message.trim());
            support.noteFromUser(userId, chat);
        } catch (Exception e) {
            log.warn("Join-business chat note was not saved: {}", e.getMessage());
        }
        boolean emailed = false;
        try {
            var cfg = mailSettings.load();
            String to = cfg.smtpFrom();
            if (to != null && !to.isBlank()) {
                Map<String, String> vars = new LinkedHashMap<>();
                vars.put("name", esc(user.fullName()));
                vars.put("email", esc(user.email()));
                vars.put("phone", esc(user.phone()));
                vars.put("businessName", esc(biz));
                vars.put("city", esc(city));
                vars.put("message", esc(message).replace("\n", "<br/>"));
                String subject = ConfigTemplates.apply(
                        configs.resolveOr("JOIN_BUSINESS_SUBJECT", "DEFAULT", DEFAULT_SUBJECT), vars);
                String html = ConfigTemplates.apply(
                        configs.resolveOr("JOIN_BUSINESS_BODY", "DEFAULT", DEFAULT_BODY), vars);
                mailer.sendHtml(cfg, to, subject, html);
                enquiries.markEmailed(id);
                emailed = true;
            }
        } catch (Exception e) {
            log.warn("Join-business email was not sent: {}", e.getMessage());
        }
        return new EnquiryView(id, emailed);
    }

    @Transactional(readOnly = true)
    public List<AdminEnquiryView> mine() {
        UUID userId = CurrentUser.id();
        return enquiries.listByUser(userId).stream()
                .map(r -> new AdminEnquiryView(
                        r.id(), r.userId(), r.fullName(), r.email(), r.phone(),
                        r.businessName(), r.city(), r.message(), r.status(), r.createdAt()))
                .toList();
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView approveAsNewBusiness(
            UUID enquiryId, String name, String slug, String businessType, String timezone, String currency) {
        if (enquiryId == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enquiry is required.");
        }
        EnquiryHolder found = tenants.call(TenantScope.platformAdmin().withUser(CurrentUser.id()), () -> {
            var enquiry = enquiries.findById(enquiryId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Join request not found."));
            if ("CLOSED".equals(enquiry.status())) {
                throw new ApiException(ErrorCode.CONFLICT, "This request is already closed.");
            }
            var user = users.findById(enquiry.userId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "That person no longer has an account."));
            if (user.tenantId() != null) {
                throw new ApiException(ErrorCode.CONFLICT, "They already own or belong to a business.");
            }
            String tenantName = blank(name) == null ? enquiry.businessName() : name.trim();
            return new EnquiryHolder(enquiry.userId(), tenantName);
        });
        TenantView created = tenantService.createForExistingOwner(
                found.userId(), found.name(), slug, businessType, timezone, currency);
        tenants.run(TenantScope.platformAdmin().withUser(CurrentUser.id()), () -> {
            enquiries.markClosed(enquiryId);
            try {
                support.noteFromUser(
                        found.userId(),
                        "Your business \"" + created.name() + "\" is ready. Sign in and switch to it — you are the owner.");
            } catch (Exception e) {
                log.warn("Approve chat note was not saved: {}", e.getMessage());
            }
        });
        return created;
    }

    private record EnquiryHolder(UUID userId, String name) {}

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional(readOnly = true)
    public List<AdminEnquiryView> list() {
        return enquiries.list().stream()
                .map(r -> new AdminEnquiryView(
                        r.id(), r.userId(), r.fullName(), r.email(), r.phone(),
                        r.businessName(), r.city(), r.message(), r.status(), r.createdAt()))
                .toList();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record EnquiryView(UUID id, boolean emailed) {}

    public record AdminEnquiryView(
            UUID id,
            UUID userId,
            String fullName,
            String email,
            String phone,
            String businessName,
            String city,
            String message,
            String status,
            Instant createdAt
    ) {}
}
