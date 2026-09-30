package com.feesaas.auth.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BootstrapResponse(
        User user,
        List<String> permissions,
        Tenant tenant,
        List<DashboardWidget> dashboard,
        List<UserContext> contexts,
        UserContext activeContext,
        boolean needsOnboarding
) {
    public record User(UUID id, String fullName, String role, UUID tenantId) {}

    public record Tenant(
            UUID id,
            String name,
            String slug,
            String businessType,
            String status,
            String timezone,
            String currency,
            Map<String, String> labels,
            List<String> modules,
            String logoBase64,
            String displayName,
            String accentColor
    ) {}

    public record DashboardWidget(String id, String module) {}

    public record UserContext(
            String kind,
            String role,
            UUID tenantId,
            UUID workspaceId,
            UUID groupId,
            String label
    ) {}
}
