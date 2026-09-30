package com.feesaas.fee;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.feesaas.fee.application.FeePlanService;
import com.feesaas.fee.application.FeePlanService.UpsertPlanCommand;
import com.feesaas.fee.infra.FeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository.PlanRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantScope;
import java.util.Optional;
import java.util.UUID;
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
}
