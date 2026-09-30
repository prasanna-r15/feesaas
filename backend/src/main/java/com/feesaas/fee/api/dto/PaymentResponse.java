package com.feesaas.fee.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String receiptNo,
        String customerName,
        String customerCode,
        long amountMinor,
        String currency,
        String amountLabel,
        String method,
        String referenceNo,
        LocalDate paidOn,
        String status,
        String voidReason
) {}
