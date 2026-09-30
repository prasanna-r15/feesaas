package com.feesaas.fee.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CollectPaymentRequest(
        @Positive Long amountMinor,
        @Size(max = 16) String method,
        @Size(max = 80) String referenceNo,
        LocalDate paidOn
) {}
