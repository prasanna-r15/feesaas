package com.feesaas.fee.application;

import com.feesaas.fee.infra.CustomerFeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository.PlanRow;
import com.feesaas.fee.infra.FeeRepository;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(FeeEnrollmentService.class);

    private final FeePlanRepository plans;
    private final CustomerFeePlanRepository enrollments;
    private final FeeRepository fees;

    public FeeEnrollmentService(
            FeePlanRepository plans,
            CustomerFeePlanRepository enrollments,
            FeeRepository fees) {
        this.plans = plans;
        this.enrollments = enrollments;
        this.fees = fees;
    }

    @Transactional
    public void enrollAndGenerate(UUID customerId, LocalDate dueDate) {
        enrollAndGenerate(customerId, dueDate, null);
    }

    @Transactional
    public void enrollAndGenerate(UUID customerId, LocalDate dueDate, UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!enrollments.customerInTenant(tenantId, customerId)) {
            log.warn("Skip fee enrollment; customer {} is not in tenant {}", customerId, tenantId);
            return;
        }
        LocalDate due = dueDate == null ? LocalDate.now().plusDays(14) : dueDate;
        UUID resolved = planId != null ? planId : plans.findDefaultOrAny().orElse(null);
        if (resolved == null) {
            log.warn("Skip fee enrollment; no fee plan for tenant {}", tenantId);
            return;
        }
        PlanRow plan = plans.findById(resolved)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fee plan not found."));
        var active = enrollments.findActive(customerId);
        if (active.isEmpty()) {
            UUID cfpId = enrollments.insert(tenantId, customerId, plan.id(), due);
            generateFor(tenantId, customerId, cfpId, plan, due);
            return;
        }
        if (active.get().planId().equals(plan.id())) {
            generateFor(tenantId, customerId, active.get().id(), plan, due);
            fees.updateOpenDueDate(customerId, due);
            return;
        }
        fees.cancelUnpaidOpen(active.get().id());
        enrollments.end(active.get().id());
        UUID cfpId = enrollments.insert(tenantId, customerId, plan.id(), due);
        generateFor(tenantId, customerId, cfpId, plan, due);
    }

    @Transactional
    public void syncDueDate(UUID customerId, LocalDate dueDate) {
        if (dueDate == null) {
            return;
        }
        if (enrollments.findActiveId(customerId).isEmpty()) {
            enrollAndGenerate(customerId, dueDate, null);
            return;
        }
        fees.updateOpenDueDate(customerId, dueDate);
    }

    @Transactional
    public void backfillCurrentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        for (var row : enrollments.customersNeedingEnrollment(tenantId)) {
            enrollQuietly(row.customerId(), row.dueDate());
        }
        for (var row : enrollments.customersNeedingCurrentFee(tenantId)) {
            enrollQuietly(row.customerId(), row.dueDate());
        }
    }

    private void enrollQuietly(UUID customerId, LocalDate dueDate) {
        try {
            enrollAndGenerate(customerId, dueDate, null);
        } catch (DataIntegrityViolationException e) {
            log.warn("Skip fee enrollment for customer {}: {}", customerId, e.getMostSpecificCause().getMessage());
        } catch (ApiException e) {
            log.warn("Skip fee enrollment for customer {}: {}", customerId, e.getMessage());
        }
    }

    private void generateFor(UUID tenantId, UUID customerId, UUID cfpId, PlanRow plan, LocalDate due) {
        LocalDate periodStart = due.withDayOfMonth(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        fees.insertIgnoreDuplicate(
                tenantId,
                customerId,
                cfpId,
                periodStart,
                periodEnd,
                due,
                plan.graceDays(),
                plan.amountMinor(),
                plan.currency());
    }
}
