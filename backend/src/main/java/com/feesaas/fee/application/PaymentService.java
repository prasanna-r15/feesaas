package com.feesaas.fee.application;

import com.feesaas.fee.infra.FeeRepository;
import com.feesaas.fee.infra.PaymentRepository;
import com.feesaas.fee.infra.PaymentRepository.PaymentRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final FeeRepository fees;

    public PaymentService(PaymentRepository payments, FeeRepository fees) {
        this.payments = payments;
        this.fees = fees;
    }

    @PreAuthorize("hasPermission(null, 'payments.view')")
    @Transactional(readOnly = true)
    public List<PaymentView> list() {
        TenantContext.requireTenantId();
        return payments.list().stream().map(this::toView).toList();
    }

    @PreAuthorize("hasPermission(null, 'payments.view')")
    @Transactional(readOnly = true)
    public PaymentView get(UUID id) {
        TenantContext.requireTenantId();
        return toView(payments.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Receipt not found.")));
    }

    @PreAuthorize("hasPermission(null, 'payments.void')")
    @Transactional
    public PaymentView voidPayment(UUID id, String reason) {
        TenantContext.requireTenantId();
        var row = payments.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Receipt not found."));
        if (!"RECORDED".equals(row.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "This receipt is already void.");
        }
        String note = reason == null || reason.isBlank() ? "Voided" : reason.trim();
        if (payments.markVoid(id, note, CurrentUser.id()) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "This receipt is already void.");
        }
        for (var alloc : payments.allocations(id)) {
            var locked = fees.lockById(alloc.feeId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fee not found for this receipt."));
            long newPaid = Math.max(0, locked.paidMinor() - alloc.amountMinor());
            long due = locked.grossMinor() + locked.adjustmentsMinor();
            String status = newPaid <= 0 ? "PENDING" : (newPaid >= due ? "PAID" : "PARTIALLY_PAID");
            if (fees.applyPayment(locked.id(), newPaid, status, locked.version()) == 0) {
                throw new ApiException(ErrorCode.CONFLICT, "Fee was updated by someone else. Try again.");
            }
        }
        return toView(payments.findById(id).orElseThrow());
    }

    private PaymentView toView(PaymentRow row) {
        return new PaymentView(
                row.id(),
                row.receiptNo(),
                row.customerName(),
                row.customerCode(),
                row.amountMinor(),
                row.currency(),
                FeeService.formatMoney(row.amountMinor(), row.currency()),
                row.method(),
                row.referenceNo(),
                row.paidOn(),
                row.status(),
                row.voidReason());
    }

    public record PaymentView(
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
}
