package com.feesaas.fee.api;

import com.feesaas.fee.api.dto.CollectPaymentRequest;
import com.feesaas.fee.api.dto.PendingFeeResponse;
import com.feesaas.fee.api.dto.PendingSummaryResponse;
import com.feesaas.fee.api.dto.RemindResponse;
import com.feesaas.fee.application.FeeService.RemindView;
import com.feesaas.fee.application.FeeService;
import com.feesaas.fee.application.FeeService.CollectCommand;
import com.feesaas.fee.application.FeeService.PendingFeeView;
import com.feesaas.fee.application.FeeService.PendingSummaryView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FeeController {

    private final FeeService fees;

    public FeeController(FeeService fees) {
        this.fees = fees;
    }

    @GetMapping("/fees/pending")
    public List<PendingFeeResponse> pending(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) UUID branchId) {
        return fees.pending(bucket, branchId).stream().map(FeeController::toFee).toList();
    }

    @GetMapping("/fees/pending/summary")
    public PendingSummaryResponse summary() {
        PendingSummaryView view = fees.summary();
        return new PendingSummaryResponse(
                view.allCount(), view.allMinor(), view.allLabel(),
                view.overdueCount(), view.overdueMinor(), view.overdueLabel(),
                view.todayCount(), view.todayMinor(), view.todayLabel(),
                view.currency());
    }

    @PostMapping("/fees/{id}/collect")
    public PendingFeeResponse collect(@PathVariable UUID id, @Valid @RequestBody(required = false) CollectPaymentRequest request) {
        CollectPaymentRequest body = request == null ? new CollectPaymentRequest(null, "CASH", null, null) : request;
        return toFee(fees.collect(id, new CollectCommand(body.amountMinor(), body.method(), body.referenceNo(), body.paidOn())));
    }

    @PostMapping("/fees/{id}/remind")
    @PreAuthorize("hasPermission(null, 'reminders.send')")
    public RemindResponse remind(@PathVariable UUID id) {
        RemindView view = fees.remind(id);
        return new RemindResponse(
                view.channel(), view.body(), view.waLink(), view.smsLink(),
                view.tenantWhatsapp(), view.tenantPhone());
    }

    private static PendingFeeResponse toFee(PendingFeeView view) {
        return new PendingFeeResponse(
                view.id(),
                view.customerId(),
                view.customerName(),
                view.customerCode(),
                view.phone(),
                view.hasWhatsapp(),
                view.dueDate(),
                view.grossMinor(),
                view.paidMinor(),
                view.outstandingMinor(),
                view.currency(),
                view.outstandingLabel(),
                view.status(),
                view.effectiveStatus(),
                view.receiptNo(),
                view.planName(),
                view.branchId(),
                view.branchName());
    }
}
