package com.feesaas.report.api.dto;

import java.time.LocalDate;
import java.util.List;

public record ReportOverviewResponse(
        long members,
        long pendingCount,
        long outstandingMinor,
        String outstandingLabel,
        long overdueCount,
        long overdueMinor,
        String overdueLabel,
        long collectedTodayMinor,
        String collectedTodayLabel,
        long collectedMonthMinor,
        String collectedMonthLabel,
        long feesTodayMinor,
        String feesTodayLabel,
        long feesMonthMinor,
        String feesMonthLabel,
        long extrasTodayMinor,
        String extrasTodayLabel,
        long extrasMonthMinor,
        String extrasMonthLabel,
        String currency,
        List<PlanBreakdown> byPlan,
        List<RecentPayment> recentPayments,
        List<Period> periods
) {
    public record Period(int year, int month) {}

    public record PlanBreakdown(String name, long feeCount, long outstandingMinor, String outstandingLabel) {}

    public record RecentPayment(
            String receiptNo,
            String customerName,
            long amountMinor,
            String amountLabel,
            String method,
            LocalDate paidOn,
            String source,
            String category,
            String branchName
    ) {}
}
