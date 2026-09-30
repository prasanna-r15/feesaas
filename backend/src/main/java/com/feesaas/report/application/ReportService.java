package com.feesaas.report.application;

import com.feesaas.fee.application.FeeService;
import com.feesaas.report.infra.ReportRepository;
import com.feesaas.shared.export.Spreadsheets;
import com.feesaas.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reports;

    public ReportService(ReportRepository reports) {
        this.reports = reports;
    }

    @PreAuthorize("hasPermission(null, 'reports.view')")
    @Transactional(readOnly = true)
    public OverviewView overview() {
        TenantContext.requireTenantId();
        var row = reports.overview();
        String currency = "INR";
        long collectedToday = row.feesTodayMinor() + row.extrasTodayMinor();
        long collectedMonth = row.feesMonthMinor() + row.extrasMonthMinor();
        return new OverviewView(
                row.members(),
                row.pendingCount(),
                row.outstandingMinor(),
                FeeService.formatMoney(row.outstandingMinor(), currency),
                row.overdueCount(),
                row.overdueMinor(),
                FeeService.formatMoney(row.overdueMinor(), currency),
                collectedToday,
                FeeService.formatMoney(collectedToday, currency),
                collectedMonth,
                FeeService.formatMoney(collectedMonth, currency),
                row.feesTodayMinor(),
                FeeService.formatMoney(row.feesTodayMinor(), currency),
                row.feesMonthMinor(),
                FeeService.formatMoney(row.feesMonthMinor(), currency),
                row.extrasTodayMinor(),
                FeeService.formatMoney(row.extrasTodayMinor(), currency),
                row.extrasMonthMinor(),
                FeeService.formatMoney(row.extrasMonthMinor(), currency),
                currency,
                reports.byPlan().stream()
                        .map(p -> new PlanBreakdownView(
                                p.name(),
                                p.feeCount(),
                                p.outstandingMinor(),
                                FeeService.formatMoney(p.outstandingMinor(), currency)))
                        .toList(),
                reports.recentPayments().stream()
                        .map(p -> new RecentPaymentView(
                                p.receiptNo(),
                                p.customerName(),
                                p.amountMinor(),
                                FeeService.formatMoney(p.amountMinor(), p.currency()),
                                p.method(),
                                p.paidOn(),
                                p.source(),
                                p.category() == null ? "" : p.category(),
                                p.branchName() == null ? "" : p.branchName()))
                        .toList());
    }

    @PreAuthorize("hasPermission(null, 'reports.view')")
    @Transactional(readOnly = true)
    public ExportFile export(String format) {
        TenantContext.requireTenantId();
        List<String> headers = List.of(
                "Type", "Receipt", "Member", "Code", "Branch", "Amount", "Currency", "Method", "Reference", "Paid on", "Status");
        List<List<String>> rows = reports.exportCollections().stream()
                .map(p -> List.of(
                        n(p.source()),
                        n(p.receiptNo()),
                        n(p.customerName()),
                        n(p.customerCode()),
                        n(p.branchName()),
                        FeeService.formatMoney(p.amountMinor(), p.currency()),
                        n(p.currency()),
                        n(p.method()),
                        n(p.referenceNo()),
                        p.paidOn().toString(),
                        n(p.status())))
                .toList();
        boolean excel = format == null || format.isBlank() || "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
        if (excel) {
            return new ExportFile(
                    "duemate-collections.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    Spreadsheets.xlsx("Collections", headers, rows));
        }
        return new ExportFile(
                "duemate-collections.csv",
                "text/csv",
                Spreadsheets.csv(headers, rows));
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }

    public record ExportFile(String filename, String contentType, byte[] body) {}

    public record OverviewView(
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
            List<PlanBreakdownView> byPlan,
            List<RecentPaymentView> recentPayments
    ) {}

    public record PlanBreakdownView(String name, long feeCount, long outstandingMinor, String outstandingLabel) {}

    public record RecentPaymentView(
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
