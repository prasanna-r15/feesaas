package com.feesaas.fee.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateFeePlanRequest(
        @NotBlank @Size(max = 80) String name,
        @Positive long amountMinor,
        @Size(max = 20) String billingCycle,
        Integer graceDays,
        Boolean isDefault
) {}
