package com.feesaas.tenant.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feesaas.configuration.infra.PresetRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.tenant.infra.TenantRepository.TenantRow;
import com.feesaas.user.application.OwnerProvisioner;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final PresetRepository presets;
    private final OwnerProvisioner owners;
    private final PasswordEncoder passwords;
    private final TenantExecutor executor;
    private final ObjectMapper json;

    public TenantService(
            TenantRepository tenants,
            PresetRepository presets,
            OwnerProvisioner owners,
            PasswordEncoder passwords,
            TenantExecutor executor,
            ObjectMapper json) {
        this.tenants = tenants;
        this.presets = presets;
        this.owners = owners;
        this.passwords = passwords;
        this.executor = executor;
        this.json = json;
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView create(CreateTenantCommand cmd) {
        var preset = presets.findByCode(cmd.businessType())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown business type."));
        List<String> modules = readModules(preset.modulesJson());
        String logo = TenantLogos.normalize(cmd.logoBase64());
        return executor.call(TenantScope.platformAdmin(), () -> {
            UUID tenantId;
            try {
                tenantId = tenants.insert(
                        cmd.name(), cmd.slug(), cmd.businessType(), cmd.timezone(), cmd.currency());
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(ErrorCode.CONFLICT, "Slug is already in use.");
            }
            tenants.insertSettings(tenantId, preset.labelsJson());
            for (String module : modules) {
                tenants.insertModule(tenantId, module);
            }
            if (logo != null) {
                tenants.updateLogo(tenantId, logo);
            }
            UUID ownerId = owners.createOwner(
                    tenantId, cmd.ownerEmail(), cmd.ownerPhone(), cmd.ownerFullName(),
                    passwords.encode(cmd.ownerPassword()));
            TenantRow row = tenants.findById(tenantId).orElseThrow();
            return toView(row, modules);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<TenantView> list(String query, String status, String plan, String businessType) {
        return executor.call(TenantScope.platformAdmin(), () ->
                tenants.list(query, status, plan, businessType).stream()
                        .map(row -> toView(row, tenants.modules(row.id())))
                        .toList());
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView get(UUID id) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            TenantRow row = tenants.findById(id)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tenant not found."));
            return toView(row, tenants.modules(row.id()));
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView patch(UUID id, PatchTenantCommand cmd) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            try {
                if (tenants.updateProfile(id, cmd.name(), cmd.slug(), cmd.timezone(), cmd.currency()) == 0) {
                    throw new ApiException(ErrorCode.NOT_FOUND, "Tenant not found.");
                }
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(ErrorCode.CONFLICT, "Slug is already in use.");
            }
            if (Boolean.TRUE.equals(cmd.clearLogo())) {
                tenants.updateLogo(id, null);
            } else if (cmd.logoBase64() != null) {
                tenants.updateLogo(id, TenantLogos.normalize(cmd.logoBase64()));
            }
            if (Boolean.TRUE.equals(cmd.clearCustomLimits())) {
                tenants.clearCustomLimits(id);
            }
            tenants.updateOps(
                    id,
                    blank(cmd.displayName()),
                    accent(cmd.accentColor()),
                    cmd.trialEndsAt(),
                    cmd.graceDays(),
                    billing(cmd.billingStatus()),
                    Boolean.TRUE.equals(cmd.clearCustomLimits()) ? null : cmd.customMaxMembers(),
                    Boolean.TRUE.equals(cmd.clearCustomLimits()) ? null : cmd.customMaxStaff());
            if (cmd.phone() != null || cmd.whatsappNumber() != null) {
                var contact = tenants.findContact(id).orElse(null);
                String phone = cmd.phone() != null ? blank(cmd.phone()) : (contact == null ? null : contact.phone());
                String wa = cmd.whatsappNumber() != null ? blank(cmd.whatsappNumber()) : (contact == null ? null : contact.whatsappNumber());
                tenants.updateContact(id, phone, wa);
            }
            TenantRow row = tenants.findById(id).orElseThrow();
            return toView(row, tenants.modules(row.id()));
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView setPlan(UUID id, String planCode) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            String code = planCode == null ? "" : planCode.trim().toUpperCase();
            if (!code.equals("FREE") && !code.equals("PRO")) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Use FREE or PRO.");
            }
            if (tenants.updatePlan(id, code) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Tenant not found.");
            }
            if ("PRO".equals(code)) {
                tenants.startTrialIfMissing(id);
            }
            TenantRow row = tenants.findById(id).orElseThrow();
            return toView(row, tenants.modules(row.id()));
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView setStatus(UUID id, String status) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            if (tenants.updateStatus(id, status) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Tenant not found.");
            }
            TenantRow row = tenants.findById(id).orElseThrow();
            return toView(row, tenants.modules(row.id()));
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantView delete(UUID id) {
        return setStatus(id, "CANCELLED");
    }

    private List<String> readModules(String modulesJson) {
        try {
            return json.readValue(modulesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Invalid preset modules JSON", e);
        }
    }

    private static TenantView toView(TenantRow row, List<String> modules) {
        return new TenantView(
                row.id(), row.name(), row.slug(), row.businessType(), row.status(),
                row.timezone(), row.currency(), row.createdAt(), modules, row.ownerId(),
                row.planCode(), row.maxMembers(), row.maxStaff(),
                row.hasLogo(), row.logoBase64(),
                row.displayName(), row.accentColor(), row.trialEndsAt(), row.graceDays(),
                row.billingStatus(), row.customMaxMembers(), row.customMaxStaff(),
                row.phone(), row.whatsappNumber(), row.memberCount(), row.staffCount(),
                row.lastLoginAt(), row.lastCollectionOn(),
                row.ownerFullName(), row.ownerEmail(), row.ownerPhone());
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String accent(String value) {
        String color = blank(value);
        if (color == null) {
            return null;
        }
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Accent color must look like #0F766E.");
        }
        return color.toUpperCase();
    }

    private static String billing(String value) {
        String status = blank(value);
        if (status == null) {
            return null;
        }
        status = status.toUpperCase();
        if (!status.equals("OK") && !status.equals("PAST_DUE") && !status.equals("UNPAID")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Billing status must be OK, PAST_DUE, or UNPAID.");
        }
        return status;
    }

    public record CreateTenantCommand(
            String name,
            String slug,
            String businessType,
            String timezone,
            String currency,
            String ownerFullName,
            String ownerEmail,
            String ownerPhone,
            String ownerPassword,
            String logoBase64
    ) {}

    public record PatchTenantCommand(
            String name,
            String slug,
            String timezone,
            String currency,
            String logoBase64,
            Boolean clearLogo,
            String displayName,
            String accentColor,
            Instant trialEndsAt,
            Integer graceDays,
            String billingStatus,
            Integer customMaxMembers,
            Integer customMaxStaff,
            Boolean clearCustomLimits,
            String phone,
            String whatsappNumber
    ) {}

    public record TenantView(
            UUID id,
            String name,
            String slug,
            String businessType,
            String status,
            String timezone,
            String currency,
            Instant createdAt,
            List<String> modules,
            UUID ownerId,
            String planCode,
            int maxMembers,
            int maxStaff,
            boolean hasLogo,
            String logoBase64,
            String displayName,
            String accentColor,
            Instant trialEndsAt,
            int graceDays,
            String billingStatus,
            Integer customMaxMembers,
            Integer customMaxStaff,
            String phone,
            String whatsappNumber,
            long memberCount,
            long staffCount,
            Instant lastLoginAt,
            java.time.LocalDate lastCollectionOn,
            String ownerFullName,
            String ownerEmail,
            String ownerPhone
    ) {}
}
