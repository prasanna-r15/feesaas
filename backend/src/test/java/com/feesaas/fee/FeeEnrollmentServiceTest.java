package com.feesaas.fee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.feesaas.fee.application.FeeEnrollmentService;
import com.feesaas.fee.infra.CustomerFeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository;
import com.feesaas.fee.infra.FeePlanRepository.PlanRow;
import com.feesaas.fee.infra.FeeRepository;
import com.feesaas.shared.tenancy.TenantContext;
import com.feesaas.shared.tenancy.TenantScope;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeEnrollmentServiceTest {

    @Mock
    FeePlanRepository plans;

    @Mock
    CustomerFeePlanRepository enrollments;

    @Mock
    FeeRepository fees;

    @InjectMocks
    FeeEnrollmentService enrollment;

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID customerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID planId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Test
    void enrollSkipsWhenGymHasNoFeePlan() {
        when(enrollments.customerInTenant(tenantId, customerId)).thenReturn(true);
        when(plans.findDefaultOrAny()).thenReturn(Optional.empty());
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            enrollment.enrollAndGenerate(customerId, LocalDate.of(2026, 10, 1));
            return null;
        });
        verify(enrollments, never()).insert(any(), any(), any(), any());
        verifyNoInteractions(fees);
    }

    @Test
    void enrollUsesExistingPlanWhenPresent() {
        when(enrollments.customerInTenant(tenantId, customerId)).thenReturn(true);
        when(plans.findDefaultOrAny()).thenReturn(Optional.of(planId));
        when(plans.findById(planId)).thenReturn(Optional.of(new PlanRow(
                planId, "Monthly", 150_000, "INR", "MONTHLY", 0, true)));
        when(enrollments.findActive(customerId)).thenReturn(Optional.empty());
        TenantContext.callAs(TenantScope.tenant(tenantId), () -> {
            enrollment.enrollAndGenerate(customerId, LocalDate.of(2026, 10, 1));
            return null;
        });
        verify(enrollments).insert(tenantId, customerId, planId, LocalDate.of(2026, 10, 1));
    }
}
