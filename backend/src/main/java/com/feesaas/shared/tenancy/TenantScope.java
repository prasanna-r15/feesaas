package com.feesaas.shared.tenancy;

import java.util.UUID;

/**
 * Who a request is acting for.
 * tenantId == null && !platform && PERSONAL/GROUP -> personal or group APIs (fee RLS still fail-closed).
 * tenantId != null -> business tenant user.
 * platform -> platform super admin.
 */
public record TenantScope(
        UUID tenantId,
        boolean platform,
        UUID userId,
        String ctx,
        UUID workspaceId,
        UUID groupId
) {

    public static final TenantScope NONE = new TenantScope(null, false, null, null, null, null);

    public static TenantScope tenant(UUID tenantId) {
        return new TenantScope(java.util.Objects.requireNonNull(tenantId), false, null, "BUSINESS", null, null);
    }

    public static TenantScope platformAdmin() {
        return new TenantScope(null, true, null, "PLATFORM", null, null);
    }

    public static TenantScope platformFor(UUID tenantId) {
        return new TenantScope(java.util.Objects.requireNonNull(tenantId), true, null, "PLATFORM", null, null);
    }

    public static TenantScope personal(UUID userId, UUID workspaceId) {
        return new TenantScope(null, false, java.util.Objects.requireNonNull(userId), "PERSONAL", workspaceId, null);
    }

    public static TenantScope group(UUID userId, UUID groupId) {
        return new TenantScope(null, false, java.util.Objects.requireNonNull(userId), "GROUP", null, groupId);
    }

    public TenantScope withUser(UUID actor) {
        return new TenantScope(tenantId, platform, actor, ctx, workspaceId, groupId);
    }

    public boolean personal() {
        return "PERSONAL".equals(ctx);
    }

    public boolean group() {
        return "GROUP".equals(ctx);
    }
}
