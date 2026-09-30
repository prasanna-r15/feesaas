package com.feesaas.fee.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PendingFeeResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String customerCode,
        String phone,
        boolean hasWhatsapp,
        LocalDate dueDate,
        long grossMinor,
        long paidMinor,
        long outstandingMinor,
        String currency,
        String outstandingLabel,
        String status,
        String effectiveStatus,
        String receiptNo,
        String planName,
        UUID branchId,
        String branchName
) {}
