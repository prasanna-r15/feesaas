package com.feesaas.notify.application;

import com.feesaas.config.application.PlatformConfigService;
import com.feesaas.fee.application.FeeService;
import com.feesaas.fee.infra.FeeRepository;
import com.feesaas.notify.infra.PlatformMailSettingsRepository;
import com.feesaas.notify.infra.PlatformMailSettingsRepository.SettingsRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.shared.text.ConfigTemplates;
import com.feesaas.tenant.infra.TenantRepository;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingDuesMailService {

    private static final Logger log = LoggerFactory.getLogger(PendingDuesMailService.class);
    private static final String DEFAULT_SUBJECT = "Pending dues · {{tenantName}} · {{pendingCount}}";
    private static final String DEFAULT_BODY = """
            <p>Hi {{ownerName}},</p>
            <p>{{tenantName}} currently has <b>{{pendingCount}}</b> pending dues totalling <b>{{total}}</b>.</p>
            <table border='1' cellpadding='6' cellspacing='0'>
            <tr><th>Member</th><th>Due</th><th>Plan</th><th>Outstanding</th></tr>
            {{rows}}
            </table>
            <p>This is an automatic DueMate digest.</p>
            """;

    private final PlatformMailSettingsRepository settings;
    private final TenantRepository tenants;
    private final FeeRepository fees;
    private final TenantExecutor executor;
    private final PlatformConfigService configs;

    public PendingDuesMailService(
            PlatformMailSettingsRepository settings,
            TenantRepository tenants,
            FeeRepository fees,
            TenantExecutor executor,
            PlatformConfigService configs) {
        this.settings = settings;
        this.tenants = tenants;
        this.fees = fees;
        this.executor = executor;
        this.configs = configs;
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional(readOnly = true)
    public SettingsView view() {
        return toView(settings.load());
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    @Transactional
    public SettingsView save(
            boolean enabled,
            String cronExpr,
            String timezone,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            String smtpFrom) {
        if (cronExpr == null || cronExpr.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a schedule.");
        }
        boolean updatePassword = smtpPassword != null && !smtpPassword.isBlank();
        settings.save(
                enabled,
                cronExpr.trim(),
                timezone == null || timezone.isBlank() ? "Asia/Kolkata" : timezone.trim(),
                blankToNull(smtpHost),
                smtpPort <= 0 ? 587 : smtpPort,
                blankToNull(smtpUsername),
                updatePassword ? smtpPassword : null,
                blankToNull(smtpFrom),
                updatePassword);
        return toView(settings.load());
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public RunResult runNow() {
        return sendAll("manual");
    }

    public void runScheduled() {
        SettingsRow row = executor.call(TenantScope.platformAdmin(), settings::load);
        if (!row.enabled()) {
            return;
        }
        sendAll("cron");
    }

    private RunResult sendAll(String trigger) {
        SettingsRow cfg = executor.call(TenantScope.platformAdmin(), settings::load);
        if (cfg.smtpHost() == null || cfg.smtpHost().isBlank() || cfg.smtpFrom() == null || cfg.smtpFrom().isBlank()) {
            if ("manual".equals(trigger)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Set SMTP host and from-address first. Brevo free SMTP works: smtp-relay.brevo.com port 587.");
            }
            log.warn("Pending dues email skipped: SMTP is not configured");
            return new RunResult(0, 0, "SMTP is not configured");
        }
        List<UUID> ids = executor.call(TenantScope.platformAdmin(), tenants::idsActive);
        int sent = 0;
        int skipped = 0;
        StringBuilder notes = new StringBuilder();
        JavaMailSenderImpl mailer = mailer(cfg);
        for (UUID tenantId : ids) {
            try {
                boolean mailed = executor.call(TenantScope.tenant(tenantId), () -> sendOne(cfg, mailer, tenantId));
                if (mailed) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
                notes.append(tenantId).append(": ").append(e.getMessage()).append("; ");
                log.warn("Pending dues email failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
        String result = trigger + " sent=" + sent + " skipped=" + skipped
                + (notes.isEmpty() ? "" : " " + notes);
        executor.run(TenantScope.platformAdmin(), () -> settings.markRun(result));
        return new RunResult(sent, skipped, result);
    }

    private boolean sendOne(SettingsRow cfg, JavaMailSenderImpl mailer, UUID tenantId) {
        var tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || tenant.ownerEmail() == null || tenant.ownerEmail().isBlank()) {
            return false;
        }
        var pending = fees.listPending("", null);
        if (pending.isEmpty()) {
            return false;
        }
        long total = pending.stream().mapToLong(p -> p.outstandingMinor()).sum();
        String currency = pending.getFirst().currency();
        String money = FeeService.formatMoney(total, currency);
        StringBuilder rows = new StringBuilder();
        int n = 0;
        for (var p : pending) {
            n++;
            if (n > 40) {
                rows.append("<tr><td colspan='4'>…and ").append(pending.size() - 40).append(" more</td></tr>");
                break;
            }
            rows.append("<tr><td>")
                    .append(escape(p.customerName()))
                    .append("</td><td>")
                    .append(p.dueDate())
                    .append("</td><td>")
                    .append(escape(p.planName()))
                    .append("</td><td>")
                    .append(FeeService.formatMoney(p.outstandingMinor(), p.currency()))
                    .append("</td></tr>");
        }
        String type = tenant.businessType() == null ? "DEFAULT" : tenant.businessType();
        Map<String, String> vars = Map.of(
                "ownerName", escape(tenant.ownerFullName() == null ? "there" : tenant.ownerFullName()),
                "tenantName", escape(tenant.name()),
                "pendingCount", Integer.toString(pending.size()),
                "total", money,
                "rows", rows.toString());
        String subject = ConfigTemplates.apply(configs.resolveOr("DUES_EMAIL_SUBJECT", type, DEFAULT_SUBJECT), vars);
        String html = ConfigTemplates.apply(configs.resolveOr("DUES_EMAIL_BODY", type, DEFAULT_BODY), vars);
        try {
            MimeMessage message = mailer.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(cfg.smtpFrom());
            helper.setTo(tenant.ownerEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailer.send(message);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static JavaMailSenderImpl mailer(SettingsRow cfg) {
        JavaMailSenderImpl mailer = new JavaMailSenderImpl();
        mailer.setHost(cfg.smtpHost());
        mailer.setPort(cfg.smtpPort());
        mailer.setUsername(cfg.smtpUsername());
        mailer.setPassword(cfg.smtpPassword());
        Properties props = mailer.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        return mailer;
    }

    private static SettingsView toView(SettingsRow row) {
        return new SettingsView(
                row.enabled(),
                row.cronExpr(),
                row.timezone(),
                row.smtpHost(),
                row.smtpPort(),
                row.smtpUsername(),
                row.smtpPassword() != null && !row.smtpPassword().isBlank(),
                row.smtpFrom(),
                row.lastRunAt() == null ? null : row.lastRunAt().toString(),
                row.lastResult());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record SettingsView(
            boolean enabled,
            String cronExpr,
            String timezone,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            boolean smtpPasswordSet,
            String smtpFrom,
            String lastRunAt,
            String lastResult
    ) {}

    public record RunResult(int sent, int skipped, String detail) {}
}
