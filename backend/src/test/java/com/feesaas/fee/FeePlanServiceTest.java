package com.feesaas.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.feesaas.fee.application.FeePlanService;
import com.feesaas.fee.application.FeePlanService.FeePlanView;
import com.feesaas.fee.application.FeePlanService.UpsertPlanCommand;
import com.feesaas.fee.infra.FeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository.PlanRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class FeePlanServiceTest {

    @Mock
    FeePlanRepository plans;

    @Mock
    JdbcClient jdbc;

    @Mock
    TenantExecutor executor;

    @InjectMocks
    FeePlanService service;

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID planId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Test
    void createRejectsDuplicateName() {
        when(plans.nameTaken("PT", null)).thenReturn(true);
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            assertThatThrownBy(() -> service.create(new UpsertPlanCommand("PT", 500_000L, "MONTHLY", 0, false)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("already exists");
            return null;
        });
        verify(plans, never()).insert(any(), any(), anyLong(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void deleteRejectsPlanWithMembers() {
        when(plans.findById(planId)).thenReturn(Optional.of(new PlanRow(
                planId, "PT", 500_000, "INR", "MONTHLY", 0, false)));
        when(plans.activeEnrollmentCount(planId)).thenReturn(2L);
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            assertThatThrownBy(() -> service.delete(planId))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Reassign");
            return null;
        });
    }

    @Test
    void deleteDoesNotInventAReplacementPlan() {
        when(plans.findById(planId)).thenReturn(Optional.of(new PlanRow(
                planId, "PT", 500_000, "INR", "MONTHLY", 0, true)));
        when(plans.activeEnrollmentCount(planId)).thenReturn(0L);
        when(plans.softDelete(planId)).thenReturn(1);
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            service.delete(planId);
            return null;
        });
        verify(plans).promoteDefaultIfMissing();
        org.mockito.Mockito.verifyNoMoreInteractions(plans);
    }

    @Test
    void gymListUsesCurrentTenantAndDoesNotInsert() {
        when(plans.list()).thenReturn(List.of());
        List<FeePlanView> rows = TenantContext.callAs(TenantScope.tenant(tenantId), service::list);
        assertThat(rows).isEmpty();
        verify(plans).list();
        verify(plans, never()).insert(any(), any(), anyLong(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void platformListRunsAsThatTenantAndDoesNotSeed() {
        when(executor.call(eq(TenantScope.tenant(tenantId)), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<List<FeePlanView>> work = inv.getArgument(1);
            return TenantContext.callAs(TenantScope.tenant(tenantId), work);
        });
        when(plans.list()).thenReturn(List.of());
        assertThat(service.platformList(tenantId)).isEmpty();
        verify(plans).list();
        verify(plans, never()).insert(any(), any(), anyLong(), any(), any(), anyInt(), anyBoolean());
    }
}
