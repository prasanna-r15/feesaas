package com.feesaas.fee.api.dto;

import java.util.UUID;

public record FeePlanResponse(
        UUID id,
        String name,
        long amountMinor,
        String currency,
        String amountLabel,
        String billingCycle,
        int graceDays,
        boolean isDefault
) {}
