package com.feesaas.fee.api;

import com.feesaas.fee.api.dto.CreateFeePlanRequest;
import com.feesaas.fee.api.dto.FeePlanResponse;
import com.feesaas.fee.api.dto.PatchFeePlanRequest;
import com.feesaas.fee.application.FeePlanService;
import com.feesaas.fee.application.FeePlanService.FeePlanView;
import com.feesaas.fee.application.FeePlanService.UpsertPlanCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fee-plans")
public class FeePlanController {

    private final FeePlanService plans;

    public FeePlanController(FeePlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<FeePlanResponse> list() {
        return plans.list().stream().map(FeePlanController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeePlanResponse create(@Valid @RequestBody CreateFeePlanRequest request) {
        return toResponse(plans.create(new UpsertPlanCommand(
                request.name(), request.amountMinor(), request.billingCycle(), request.graceDays(), request.isDefault())));
    }

    @GetMapping("/{id}")
    public FeePlanResponse get(@PathVariable UUID id) {
        return toResponse(plans.get(id));
    }

    @PatchMapping("/{id}")
    public FeePlanResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchFeePlanRequest request) {
        return toResponse(plans.patch(id, new UpsertPlanCommand(
                request.name(), request.amountMinor(), request.billingCycle(), request.graceDays(), request.isDefault())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        plans.delete(id);
    }

    private static FeePlanResponse toResponse(FeePlanView view) {
        return new FeePlanResponse(
                view.id(),
                view.name(),
                view.amountMinor(),
                view.currency(),
                view.amountLabel(),
                view.billingCycle(),
                view.graceDays(),
                view.isDefault());
    }
}
