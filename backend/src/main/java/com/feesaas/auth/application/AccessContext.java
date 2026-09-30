package com.feesaas.auth.application;

import java.util.UUID;

public record AccessContext(String kind, UUID tenantId, UUID workspaceId, UUID groupId) {
    public static AccessContext platform() {
        return new AccessContext("PLATFORM", null, null, null);
    }

    public static AccessContext business(UUID tenantId) {
        return new AccessContext("BUSINESS", tenantId, null, null);
    }

    public static AccessContext personal(UUID workspaceId) {
        return new AccessContext("PERSONAL", null, workspaceId, null);
    }

    public static AccessContext group(UUID groupId) {
        return new AccessContext("GROUP", null, null, groupId);
    }
}
