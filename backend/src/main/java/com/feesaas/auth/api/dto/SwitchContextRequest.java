package com.feesaas.auth.api.dto;

import java.util.UUID;

public record SwitchContextRequest(
        String kind,
        UUID tenantId,
        UUID workspaceId,
        UUID groupId,
        String deviceId
) {}
