package com.feesaas.fee.application;

import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(110)
public class FeeEnrollmentBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeeEnrollmentBackfill.class);

    private final TenantRepository tenants;
    private final TenantExecutor executor;
    private final FeeEnrollmentService enrollment;

    public FeeEnrollmentBackfill(
            TenantRepository tenants,
            TenantExecutor executor,
            FeeEnrollmentService enrollment) {
        this.tenants = tenants;
        this.executor = executor;
        this.enrollment = enrollment;
    }

    @Override
    public void run(ApplicationArguments args) {
        var gyms = executor.call(TenantScope.platformAdmin(), tenants::list);
        for (var tenant : gyms) {
            if (!"ACTIVE".equals(tenant.status()) && !"ONBOARDING".equals(tenant.status())) {
                continue;
            }
            try {
                executor.run(TenantScope.tenant(tenant.id()), enrollment::backfillCurrentTenant);
            } catch (Exception e) {
                log.warn("Fee enrollment backfill skipped for tenant {} ({}): {}",
                        tenant.id(), tenant.name(), e.getMessage());
            }
        }
        log.info("Fee enrollment backfill complete");
    }
}
