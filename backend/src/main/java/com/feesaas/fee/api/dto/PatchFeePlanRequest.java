package com.feesaas.fee.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PatchFeePlanRequest(
        @Size(max = 80) String name,
        @Positive Long amountMinor,
        @Size(max = 20) String billingCycle,
        Integer graceDays,
        Boolean isDefault
) {}
