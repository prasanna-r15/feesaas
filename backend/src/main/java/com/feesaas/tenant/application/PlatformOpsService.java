package com.feesaas.tenant.application;

import com.feesaas.auth.application.AuthService;
import com.feesaas.auth.application.AuthService.TokenPair;
import com.feesaas.auth.infra.AuthUserRepository;
import com.feesaas.customer.infra.CustomerRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.PlatformOpsRepository;
import com.feesaas.tenant.infra.PlatformOpsRepository.AuditRow;
import com.feesaas.tenant.infra.PlatformOpsRepository.InvoiceRow;
import com.feesaas.tenant.infra.PlatformOpsRepository.NoteRow;
import com.feesaas.tenant.infra.TenantRepository;
import com.feesaas.user.infra.StaffRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformOpsService {

    private static final Set<String> CORE_MODULES = Set.of("CUSTOMERS", "SETTINGS", "NOTIFICATIONS", "REPORTS");
    private static final Set<String> OPTIONAL_MODULES = Set.of(
            "FEES", "PAYMENTS", "MEMBERSHIP", "ATTENDANCE", "BRANCHES", "ADDONS", "DIET_CHARTS");

    private final TenantRepository tenants;
    private final PlatformOpsRepository ops;
    private final CustomerRepository customers;
    private final AuthUserRepository users;
    private final StaffRepository staff;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final TenantExecutor executor;
    private final TenantService tenantService;

    public PlatformOpsService(
            TenantRepository tenants,
            PlatformOpsRepository ops,
            CustomerRepository customers,
            AuthUserRepository users,
            StaffRepository staff,
            AuthService auth,
            PasswordEncoder passwords,
            TenantExecutor executor,
            TenantService tenantService) {
        this.tenants = tenants;
        this.ops = ops;
        this.customers = customers;
        this.users = users;
        this.staff = staff;
        this.auth = auth;
        this.passwords = passwords;
        this.executor = executor;
        this.tenantService = tenantService;
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantService.TenantView setModules(UUID tenantId, List<String> modules) {
        List<String> cleaned = modules == null ? List.of() : modules.stream()
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String code : cleaned) {
            if (!CORE_MODULES.contains(code) && !OPTIONAL_MODULES.contains(code)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown module: " + code);
            }
        }
        List<String> next = new java.util.ArrayList<>(CORE_MODULES);
        for (String code : cleaned) {
            if (OPTIONAL_MODULES.contains(code) && !next.contains(code)) {
                next.add(code);
            }
        }
        executor.run(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            tenants.replaceModules(tenantId, next);
            log(tenantId, "MODULES", "Updated modules");
        });
        return tenantService.get(tenantId);
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<NoteRow> notes(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            return ops.listNotes(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<NoteRow> addNote(UUID tenantId, String body) {
        if (body == null || body.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Note cannot be empty.");
        }
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            ops.insertNote(tenantId, CurrentUser.id(), body.trim());
            log(tenantId, "NOTE", "Added note");
            return ops.listNotes(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<InvoiceRow> invoices(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            return ops.listInvoices(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<InvoiceRow> addInvoice(UUID tenantId, String period, long amountMinor, String currency, LocalDate dueOn, String note) {
        if (period == null || period.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invoice period is required.");
        }
        if (amountMinor < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invoice amount cannot be negative.");
        }
        String cur = currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT);
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            ops.insertInvoice(tenantId, period.trim(), amountMinor, cur, dueOn, note);
            log(tenantId, "INVOICE", "Created invoice " + period);
            return ops.listInvoices(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<InvoiceRow> markInvoice(UUID tenantId, UUID invoiceId, String status) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!st.equals("PAID") && !st.equals("VOID")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Mark the invoice PAID or VOID.");
        }
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            if (ops.markInvoice(invoiceId, st) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Invoice not found or already closed.");
            }
            if ("PAID".equals(st)) {
                tenants.markBilling(tenantId, "OK");
            }
            log(tenantId, "INVOICE", "Invoice " + st);
            return ops.listInvoices(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<AuditRow> audit(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            return ops.listAudit(tenantId);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public void updateOwner(UUID tenantId, String fullName, String email, String phone) {
        executor.run(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            if (ops.updateOwner(tenantId, blank(fullName), blank(email), blank(phone)) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Owner account not found.");
            }
            log(tenantId, "OWNER", "Updated owner contact");
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public String resetOwnerPassword(UUID tenantId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password must be at least 8 characters.");
        }
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            UUID ownerId = ops.ownerUserId(tenantId);
            if (ownerId == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Owner account not found.");
            }
            ops.setOwnerPassword(tenantId, passwords.encode(newPassword));
            staff.revokeRefreshTokens(ownerId);
            log(tenantId, "OWNER", "Reset owner password");
            return newPassword;
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TokenPair impersonate(UUID tenantId) {
        return executor.call(TenantScope.platformAdmin(), () -> {
            require(tenantId);
            UUID ownerId = ops.ownerUserId(tenantId);
            if (ownerId == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Owner account not found.");
            }
            var user = users.findById(ownerId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Owner account not found."));
            log(tenantId, "IMPERSONATE", "Started support session");
            return auth.impersonate(user, CurrentUser.id());
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<CustomerRepository.CustomerRow> members(UUID tenantId, String query) {
        return executor.call(TenantScope.tenant(tenantId), () -> {
            require(tenantId);
            return customers.list(query, null);
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public TenantService.TenantView restore(UUID tenantId) {
        executor.run(TenantScope.platformAdmin(), () -> log(tenantId, "RESTORE", "Restored tenant"));
        return tenantService.setStatus(tenantId, "ACTIVE");
    }

    private void require(UUID tenantId) {
        tenants.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tenant not found."));
    }

    private void log(UUID tenantId, String action, String message) {
        ops.audit(tenantId, CurrentUser.id(), action, "TENANT", tenantId.toString(), message);
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
