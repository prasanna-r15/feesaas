package com.feesaas.report.api;

import com.feesaas.report.api.dto.ReportOverviewResponse;
import com.feesaas.report.api.dto.ReportOverviewResponse.Period;
import com.feesaas.report.api.dto.ReportOverviewResponse.PlanBreakdown;
import com.feesaas.report.api.dto.ReportOverviewResponse.RecentPayment;
import com.feesaas.report.application.ReportService;
import com.feesaas.report.application.ReportService.OverviewView;
import com.feesaas.report.application.ReportService.ExportFile;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public ReportOverviewResponse overview(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) LocalDate paidOn) {
        OverviewView view = reports.overview(year, month, paidOn);
        return new ReportOverviewResponse(
                view.members(),
                view.pendingCount(),
                view.outstandingMinor(),
                view.outstandingLabel(),
                view.overdueCount(),
                view.overdueMinor(),
                view.overdueLabel(),
                view.collectedTodayMinor(),
                view.collectedTodayLabel(),
                view.collectedMonthMinor(),
                view.collectedMonthLabel(),
                view.feesTodayMinor(),
                view.feesTodayLabel(),
                view.feesMonthMinor(),
                view.feesMonthLabel(),
                view.extrasTodayMinor(),
                view.extrasTodayLabel(),
                view.extrasMonthMinor(),
                view.extrasMonthLabel(),
                view.currency(),
                view.byPlan().stream()
                        .map(p -> new PlanBreakdown(p.name(), p.feeCount(), p.outstandingMinor(), p.outstandingLabel()))
                        .toList(),
                view.recentPayments().stream()
                        .map(p -> new RecentPayment(
                                p.receiptNo(),
                                p.customerName(),
                                p.amountMinor(),
                                p.amountLabel(),
                                p.method(),
                                p.paidOn(),
                                p.source(),
                                p.category(),
                                p.branchName()))
                        .toList(),
                view.periods().stream().map(p -> new Period(p.year(), p.month())).toList());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String format) {
        ExportFile file = reports.export(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename())
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.body());
    }
}
