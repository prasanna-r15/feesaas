package com.feesaas.fee.api;

import com.feesaas.fee.api.dto.PaymentResponse;
import com.feesaas.fee.application.PaymentService;
import com.feesaas.fee.application.PaymentService.PaymentView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public List<PaymentResponse> list() {
        return payments.list().stream().map(PaymentController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return toResponse(payments.get(id));
    }

    @PostMapping("/{id}/void")
    public PaymentResponse voidPayment(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return toResponse(payments.voidPayment(id, reason));
    }

    private static PaymentResponse toResponse(PaymentView view) {
        return new PaymentResponse(
                view.id(), view.receiptNo(), view.customerName(), view.customerCode(),
                view.amountMinor(), view.currency(), view.amountLabel(), view.method(),
                view.referenceNo(), view.paidOn(), view.status(), view.voidReason());
    }
}
