package com.feesaas.customer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 32) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 2000) String notes,
        @NotNull LocalDate dueDate,
        UUID feePlanId,
        Boolean hasWhatsapp,
        UUID branchId
) {}
