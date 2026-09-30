package com.feesaas.user.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        Instant createdAt,
        List<String> permissions
) {}
