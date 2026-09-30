package com.feesaas.customer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record PatchCustomerRequest(
        @Size(max = 120) String fullName,
        @Size(max = 32) String phone,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @Size(max = 2000) String notes,
        LocalDate dueDate,
        UUID feePlanId,
        Boolean hasWhatsapp,
        UUID branchId
) {}
