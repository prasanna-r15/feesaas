package com.feesaas.user.application;

import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.security.PermissionLookup;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.tenant.application.UsageGuard;
import com.feesaas.user.infra.StaffRepository;
import com.feesaas.user.infra.StaffRepository.PermissionRow;
import com.feesaas.user.infra.StaffRepository.StaffRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final StaffRepository staff;
    private final PasswordEncoder passwords;
    private final PermissionLookup permissionLookup;
    private final UsageGuard usage;

    public StaffService(StaffRepository staff, PasswordEncoder passwords, PermissionLookup permissionLookup, UsageGuard usage) {
        this.staff = staff;
        this.passwords = passwords;
        this.permissionLookup = permissionLookup;
        this.usage = usage;
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional(readOnly = true)
    public List<StaffView> list() {
        TenantContext.requireTenantId();
        return staff.listStaff().stream().map(this::toView).toList();
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional(readOnly = true)
    public StaffView get(UUID id) {
        TenantContext.requireTenantId();
        StaffRow row = staff.findStaffById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Staff member not found."));
        return toView(row);
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional
    public StaffView create(CreateStaffCommand cmd) {
        UUID tenantId = TenantContext.requireTenantId();
        usage.assertCanAddStaff();
        if (cmd.email() == null && cmd.phone() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email or phone is required.");
        }
        List<String> permissions = resolveGrants(cmd.permissions());
        UUID id = staff.insert(
                tenantId, cmd.email(), cmd.phone(), cmd.fullName(),
                passwords.encode(cmd.password()), "STAFF", "ACTIVE");
        staff.replacePermissions(tenantId, id, permissions);
        return toView(staff.findStaffById(id).orElseThrow());
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional
    public StaffView patch(UUID id, PatchStaffCommand cmd) {
        TenantContext.requireTenantId();
        if (staff.updateStaff(id, cmd.fullName(), cmd.email(), cmd.phone(), cmd.status()) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Staff member not found.");
        }
        if ("DISABLED".equals(cmd.status())) {
            staff.revokeRefreshTokens(id);
        }
        return toView(staff.findStaffById(id).orElseThrow());
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional
    public void delete(UUID id) {
        TenantContext.requireTenantId();
        if (id.equals(CurrentUser.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You cannot remove your own access.");
        }
        if (staff.disable(id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Staff member not found.");
        }
        staff.revokeRefreshTokens(id);
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional
    public StaffView replacePermissions(UUID id, List<String> permissions) {
        UUID tenantId = TenantContext.requireTenantId();
        StaffRow row = staff.findStaffById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Staff member not found."));
        staff.replacePermissions(tenantId, row.id(), resolveGrants(permissions));
        return toView(staff.findStaffById(id).orElseThrow());
    }

    @PreAuthorize("hasPermission(null, 'staff.manage')")
    @Transactional(readOnly = true)
    public List<PermissionRow> permissionCatalogue() {
        TenantContext.requireTenantId();
        return staff.tenantPermissionCatalogue();
    }

    private List<String> resolveGrants(List<String> requested) {
        List<String> wanted = requested == null || requested.isEmpty()
                ? staff.staffRoleDefaults()
                : requested;
        Set<String> actor = permissionLookup.forUser(CurrentUser.id());
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String code : wanted) {
            if (!staff.permissionExists(code)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown permission: " + code);
            }
            if (!actor.contains(code)) {
                throw new ApiException(ErrorCode.FORBIDDEN, "You cannot grant a permission you do not have.");
            }
            unique.add(code);
        }
        return new ArrayList<>(unique);
    }

    private StaffView toView(StaffRow row) {
        return new StaffView(
                row.id(), row.fullName(), row.email(), row.phone(), row.role(), row.status(),
                row.createdAt(), staff.permissionsOf(row.id()));
    }

    public record CreateStaffCommand(
            String fullName, String email, String phone, String password, List<String> permissions) {}

    public record PatchStaffCommand(String fullName, String email, String phone, String status) {}

    public record StaffView(
            UUID id,
            String fullName,
            String email,
            String phone,
            String role,
            String status,
            Instant createdAt,
            List<String> permissions
    ) {}
}
