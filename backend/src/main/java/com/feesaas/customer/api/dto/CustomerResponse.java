package com.feesaas.customer.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerCode,
        String fullName,
        String phone,
        String email,
        String status,
        String notes,
        LocalDate dueDate,
        Instant createdAt,
        boolean hasWhatsapp,
        UUID feePlanId,
        String feePlanName,
        UUID branchId,
        String branchName
) {}
