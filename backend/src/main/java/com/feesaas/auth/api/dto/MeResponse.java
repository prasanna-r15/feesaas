package com.feesaas.auth.api.dto;

import java.util.UUID;

public record MeResponse(UUID id, String fullName, String role, UUID tenantId) {}
