package com.feesaas.auth.domain;

import com.feesaas.shared.tenancy.TenantClaims;
import com.feesaas.shared.tenancy.TenantScope;
import java.time.Instant;
import java.util.UUID;

public record AuthUser(
        UUID id,
        UUID tenantId,
        String fullName,
        String passwordHash,
        String roleCode,
        String status,
        int tokenVersion,
        int failedLoginCount,
        Instant lockedUntil
) {
    public boolean platform() {
        return TenantClaims.PLATFORM_ROLE.equals(roleCode);
    }

    public TenantScope writeScope() {
        if (platform()) {
            return TenantScope.platformAdmin();
        }
        if (tenantId == null) {
            return TenantScope.personal(id, null);
        }
        return TenantScope.tenant(tenantId).withUser(id);
    }
}
