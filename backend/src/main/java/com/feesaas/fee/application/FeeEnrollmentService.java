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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeEnrollmentService {

    private final FeePlanRepository plans;
    private final CustomerFeePlanRepository enrollments;
    private final FeeRepository fees;
    private final JdbcClient jdbc;

    public FeeEnrollmentService(
            FeePlanRepository plans,
            CustomerFeePlanRepository enrollments,
            FeeRepository fees,
            JdbcClient jdbc) {
        this.plans = plans;
        this.enrollments = enrollments;
        this.fees = fees;
        this.jdbc = jdbc;
    }

    @Transactional
    public void enrollAndGenerate(UUID customerId, LocalDate dueDate) {
        enrollAndGenerate(customerId, dueDate, null);
    }

    @Transactional
    public void enrollAndGenerate(UUID customerId, LocalDate dueDate, UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate due = dueDate == null ? LocalDate.now().plusDays(14) : dueDate;
        plans.seedStarterPlans(tenantId, tenantCurrency(tenantId));
        UUID resolved = planId != null ? planId : plans.ensureDefault(tenantId, tenantCurrency(tenantId));
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
        plans.seedStarterPlans(tenantId, tenantCurrency(tenantId));
        for (var row : enrollments.customersNeedingEnrollment()) {
            enrollAndGenerate(row.customerId(), row.dueDate(), null);
        }
        for (var row : enrollments.customersNeedingCurrentFee()) {
            enrollAndGenerate(row.customerId(), row.dueDate(), null);
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

    private String tenantCurrency(UUID tenantId) {
        return jdbc.sql("select currency from tenants where id = :id")
                .param("id", tenantId)
                .query(String.class)
                .optional()
                .map(String::trim)
                .orElse("INR");
    }
}
