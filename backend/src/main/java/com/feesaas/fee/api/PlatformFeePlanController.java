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
@RequestMapping("/api/v1/platform/tenants/{tenantId}/fee-plans")
public class PlatformFeePlanController {

    private final FeePlanService plans;

    public PlatformFeePlanController(FeePlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<FeePlanResponse> list(@PathVariable UUID tenantId) {
        return plans.platformList(tenantId).stream().map(PlatformFeePlanController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<FeePlanResponse> create(@PathVariable UUID tenantId, @Valid @RequestBody CreateFeePlanRequest request) {
        return plans.platformCreate(tenantId, new UpsertPlanCommand(
                        request.name(),
                        request.amountMinor(),
                        request.billingCycle(),
                        request.graceDays(),
                        request.isDefault()))
                .stream()
                .map(PlatformFeePlanController::toResponse)
                .toList();
    }

    @PatchMapping("/{id}")
    public List<FeePlanResponse> patch(
            @PathVariable UUID tenantId, @PathVariable UUID id, @Valid @RequestBody PatchFeePlanRequest request) {
        return plans.platformPatch(tenantId, id, new UpsertPlanCommand(
                        request.name(),
                        request.amountMinor(),
                        request.billingCycle(),
                        request.graceDays(),
                        request.isDefault()))
                .stream()
                .map(PlatformFeePlanController::toResponse)
                .toList();
    }

    @DeleteMapping("/{id}")
    public List<FeePlanResponse> delete(@PathVariable UUID tenantId, @PathVariable UUID id) {
        return plans.platformDelete(tenantId, id).stream().map(PlatformFeePlanController::toResponse).toList();
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
