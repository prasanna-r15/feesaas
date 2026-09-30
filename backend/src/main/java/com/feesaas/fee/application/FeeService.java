package com.feesaas.fee.application;

import com.feesaas.fee.infra.FeeRepository;
import com.feesaas.fee.infra.FeeRepository.PendingRow;
import com.feesaas.fee.infra.PaymentRepository;
import com.feesaas.shared.contact.PhoneNumbers;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.money.Money;
import com.feesaas.shared.security.CurrentUser;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.tenant.infra.TenantRepository;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeService {

    private final FeeRepository fees;
    private final PaymentRepository payments;
    private final TenantRepository tenants;

    public FeeService(FeeRepository fees, PaymentRepository payments, TenantRepository tenants) {
        this.fees = fees;
        this.payments = payments;
        this.tenants = tenants;
    }

    @PreAuthorize("hasPermission(null, 'fees.view')")
    @Transactional(readOnly = true)
    public List<PendingFeeView> pending(String bucket, UUID branchId) {
        TenantContext.requireTenantId();
        return fees.listPending(bucket, branchId).stream().map(this::toView).toList();
    }

    @PreAuthorize("hasPermission(null, 'fees.view')")
    @Transactional(readOnly = true)
    public PendingSummaryView summary() {
        TenantContext.requireTenantId();
        var row = fees.summary();
        String currency = "INR";
        return new PendingSummaryView(
                row.allCount(),
                row.allMinor(),
                formatMoney(row.allMinor(), currency),
                row.overdueCount(),
                row.overdueMinor(),
                formatMoney(row.overdueMinor(), currency),
                row.todayCount(),
                row.todayMinor(),
                formatMoney(row.todayMinor(), currency),
                currency);
    }

    @PreAuthorize("hasPermission(null, 'payments.record')")
    @Transactional
    public PendingFeeView collect(UUID feeId, CollectCommand cmd) {
        UUID tenantId = TenantContext.requireTenantId();
        var locked = fees.lockById(feeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fee not found."));
        if ("PAID".equals(locked.status()) || "CANCELLED".equals(locked.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "This fee is already settled.");
        }
        long outstanding = locked.outstanding();
        long amount = cmd.amountMinor() == null ? outstanding : cmd.amountMinor();
        if (amount <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Payment amount must be greater than zero.");
        }
        if (amount > outstanding) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Payment cannot exceed the outstanding amount.");
        }
        String method = cmd.method() == null || cmd.method().isBlank() ? "CASH" : cmd.method().trim().toUpperCase();
        if (!List.of("CASH", "UPI", "CARD", "BANK", "OTHER").contains(method)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown payment method.");
        }
        long newPaid = locked.paidMinor() + amount;
        String status = newPaid >= locked.grossMinor() + locked.adjustmentsMinor() ? "PAID" : "PARTIALLY_PAID";
        if (fees.applyPayment(locked.id(), newPaid, status, locked.version()) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "Fee was updated by someone else. Try again.");
        }
        String receipt = payments.nextReceiptNo();
        UUID paymentId = payments.insert(
                tenantId,
                locked.customerId(),
                amount,
                locked.currency(),
                method,
                blankToNull(cmd.referenceNo()),
                cmd.paidOn() == null ? LocalDate.now() : cmd.paidOn(),
                receipt,
                CurrentUser.id());
        payments.allocate(tenantId, paymentId, locked.id(), amount);
        return fees.listPending("", null).stream()
                .filter(row -> row.id().equals(feeId))
                .findFirst()
                .map(this::toView)
                .orElse(new PendingFeeView(
                        locked.id(),
                        locked.customerId(),
                        "",
                        "",
                        null,
                        false,
                        LocalDate.now(),
                        locked.grossMinor(),
                        newPaid,
                        locked.grossMinor() + locked.adjustmentsMinor() - newPaid,
                        locked.currency(),
                        formatMoney(locked.grossMinor() + locked.adjustmentsMinor() - newPaid, locked.currency()),
                        status,
                        status,
                        receipt,
                        "",
                        null,
                        ""));
    }

    @PreAuthorize("hasPermission(null, 'reminders.send')")
    @Transactional(readOnly = true)
    public RemindView remind(UUID feeId) {
        UUID tenantId = TenantContext.requireTenantId();
        var fee = fees.findForRemind(feeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fee not found."));
        if (!PhoneNumbers.hasNumber(fee.phone())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This member has no phone number.");
        }
        var contact = tenants.findContact(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Business profile not found."));
        String amount = formatMoney(fee.outstandingMinor(), fee.currency());
        String fromLine = "";
        if (PhoneNumbers.hasNumber(contact.whatsappNumber()) || PhoneNumbers.hasNumber(contact.phone())) {
            String shown = PhoneNumbers.hasNumber(contact.whatsappNumber())
                    ? contact.whatsappNumber()
                    : contact.phone();
            fromLine = " Reach us at " + shown + ".";
        }
        String body = "Hi " + fee.customerName()
                + ", " + amount
                + " for " + fee.planName()
                + " is due on " + fee.dueDate()
                + ". Please pay " + contact.name() + "."
                + fromLine;
        String channel = fee.hasWhatsapp() ? "WHATSAPP" : "SMS";
        return new RemindView(
                channel,
                body,
                PhoneNumbers.waLink(fee.phone(), body),
                PhoneNumbers.smsLink(fee.phone(), body),
                contact.whatsappNumber(),
                contact.phone());
    }

    private PendingFeeView toView(PendingRow row) {
        return new PendingFeeView(
                row.id(),
                row.customerId(),
                row.customerName(),
                row.customerCode(),
                row.phone(),
                row.hasWhatsapp(),
                row.dueDate(),
                row.grossMinor(),
                row.paidMinor(),
                row.outstandingMinor(),
                row.currency(),
                formatMoney(row.outstandingMinor(), row.currency()),
                row.status(),
                row.effectiveStatus(),
                null,
                row.planName(),
                row.branchId(),
                row.branchName());
    }

    public static String formatMoney(long minor, String currencyCode) {
        Money money = Money.ofMinor(minor, currencyCode);
        if ("INR".equals(currencyCode)) {
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.ENGLISH);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return "₹" + nf.format(money.toMajor());
        }
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.US);
        nf.setCurrency(Currency.getInstance(currencyCode));
        return nf.format(money.toMajor());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CollectCommand(Long amountMinor, String method, String referenceNo, LocalDate paidOn) {}

    public record PendingFeeView(
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

    public record PendingSummaryView(
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

    public record RemindView(
            String channel,
            String body,
            String waLink,
            String smsLink,
            String tenantWhatsapp,
            String tenantPhone
    ) {}
}
