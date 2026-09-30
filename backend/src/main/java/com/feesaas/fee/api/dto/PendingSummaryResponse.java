package com.feesaas.fee.api.dto;

public record PendingSummaryResponse(
        long allCount,
        long allMinor,
        String allLabel,
        long overdueCount,
        long overdueMinor,
        String overdueLabel,
        long todayCount,
        long todayMinor,
        String todayLabel,
        String currency
) {}
