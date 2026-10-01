package com.feesaas.fee.application;

import com.feesaas.fee.infra.FeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository.PlanRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeePlanService {

    private static final Set<String> CYCLES = Set.of("WEEKLY", "MONTHLY", "QUARTERLY", "HALF_YEARLY", "ANNUAL");

    private final FeePlanRepository plans;
    private final JdbcClient jdbc;
    private final TenantExecutor executor;

    public FeePlanService(FeePlanRepository plans, JdbcClient jdbc, TenantExecutor executor) {
        this.plans = plans;
        this.jdbc = jdbc;
        this.executor = executor;
    }

    @PreAuthorize("hasPermission(null, 'fees.view')")
    @Transactional(readOnly = true)
    public List<FeePlanView> list() {
        return listCurrent();
    }

    @PreAuthorize("hasPermission(null, 'fees.view')")
    @Transactional(readOnly = true)
    public FeePlanView get(UUID id) {
        TenantContext.requireTenantId();
        return toView(require(id));
    }

    @PreAuthorize("hasPermission(null, 'fees.manage')")
    @Transactional
    public FeePlanView create(UpsertPlanCommand cmd) {
        return createCurrent(cmd);
    }

    @PreAuthorize("hasPermission(null, 'fees.manage')")
    @Transactional
    public FeePlanView patch(UUID id, UpsertPlanCommand cmd) {
        return patchCurrent(id, cmd);
    }

    @PreAuthorize("hasPermission(null, 'fees.manage')")
    @Transactional
    public void delete(UUID id) {
        deleteCurrent(id);
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<FeePlanView> platformList(UUID tenantId) {
        return executor.call(TenantScope.tenant(tenantId), this::listCurrent);
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<FeePlanView> platformCreate(UUID tenantId, UpsertPlanCommand cmd) {
        return executor.call(TenantScope.tenant(tenantId), () -> {
            createCurrent(cmd);
            return listCurrent();
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<FeePlanView> platformPatch(UUID tenantId, UUID id, UpsertPlanCommand cmd) {
        return executor.call(TenantScope.tenant(tenantId), () -> {
            patchCurrent(id, cmd);
            return listCurrent();
        });
    }

    @PreAuthorize("hasPermission(null, 'platform.tenants.manage')")
    public List<FeePlanView> platformDelete(UUID tenantId, UUID id) {
        return executor.call(TenantScope.tenant(tenantId), () -> {
            deleteCurrent(id);
            return listCurrent();
        });
    }

    private List<FeePlanView> listCurrent() {
        TenantContext.requireTenantId();
        return plans.list().stream().map(this::toView).toList();
    }

    private FeePlanView createCurrent(UpsertPlanCommand cmd) {
        UUID tenantId = TenantContext.requireTenantId();
        String name = requireName(cmd.name());
        long amount = requireAmount(cmd.amountMinor());
        String cycle = requireCycle(cmd.billingCycle());
        int grace = requireGrace(cmd.graceDays());
        if (plans.nameTaken(name, null)) {
            throw new ApiException(ErrorCode.CONFLICT, "A fee plan with this name already exists.");
        }
        boolean isDefault = Boolean.TRUE.equals(cmd.isDefault());
        UUID id = plans.insert(tenantId, name, amount, tenantCurrency(tenantId), cycle, grace, isDefault);
        return toView(require(id));
    }

    private FeePlanView patchCurrent(UUID id, UpsertPlanCommand cmd) {
        TenantContext.requireTenantId();
        require(id);
        String name = cmd.name() == null ? null : requireName(cmd.name());
        if (name != null && plans.nameTaken(name, id)) {
            throw new ApiException(ErrorCode.CONFLICT, "A fee plan with this name already exists.");
        }
        Long amount = cmd.amountMinor() == null ? null : requireAmount(cmd.amountMinor());
        String cycle = cmd.billingCycle() == null ? null : requireCycle(cmd.billingCycle());
        Integer grace = cmd.graceDays() == null ? null : requireGrace(cmd.graceDays());
        if (plans.update(id, name, amount, cycle, grace, cmd.isDefault()) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Fee plan not found.");
        }
        return toView(require(id));
    }

    private void deleteCurrent(UUID id) {
        TenantContext.requireTenantId();
        require(id);
        if (plans.activeEnrollmentCount(id) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "Reassign members on this plan before deleting it.");
        }
        if (plans.softDelete(id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Fee plan not found.");
        }
        plans.promoteDefaultIfMissing();
    }

    private PlanRow require(UUID id) {
        return plans.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fee plan not found."));
    }

    private FeePlanView toView(PlanRow row) {
        return new FeePlanView(
                row.id(),
                row.name(),
                row.amountMinor(),
                row.currency(),
                FeeService.formatMoney(row.amountMinor(), row.currency()),
                row.billingCycle(),
                row.graceDays(),
                row.isDefault());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Plan name is required.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 80) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Plan name is too long.");
        }
        return trimmed;
    }

    private static long requireAmount(Long amountMinor) {
        if (amountMinor == null || amountMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
        return amountMinor;
    }

    private static String requireCycle(String cycle) {
        String value = cycle == null || cycle.isBlank() ? "MONTHLY" : cycle.trim().toUpperCase();
        if (!CYCLES.contains(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown billing cycle.");
        }
        return value;
    }

    private static int requireGrace(Integer graceDays) {
        int grace = graceDays == null ? 0 : graceDays;
        if (grace < 0 || grace > 60) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Grace days must be between 0 and 60.");
        }
        return grace;
    }

    private String tenantCurrency(UUID tenantId) {
        return jdbc.sql("select currency from tenants where id = :id")
                .param("id", tenantId)
                .query(String.class)
                .optional()
                .map(String::trim)
                .orElse("INR");
    }

    public record UpsertPlanCommand(
            String name, Long amountMinor, String billingCycle, Integer graceDays, Boolean isDefault) {}

    public record FeePlanView(
            UUID id,
            String name,
            long amountMinor,
            String currency,
            String amountLabel,
            String billingCycle,
            int graceDays,
            boolean isDefault
    ) {}
}
