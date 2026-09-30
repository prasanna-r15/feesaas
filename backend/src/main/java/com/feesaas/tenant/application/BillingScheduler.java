package com.feesaas.tenant.application;

import com.feesaas.shared.tenancy.TenantExecutor;
import com.feesaas.shared.tenancy.TenantScope;
import com.feesaas.tenant.infra.TenantRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final TenantRepository tenants;
    private final TenantExecutor executor;

    public BillingScheduler(TenantRepository tenants, TenantExecutor executor) {
        this.tenants = tenants;
        this.executor = executor;
    }

    @Scheduled(initialDelay = 3_600_000, fixedDelay = 3_600_000)
    public void enforceTrials() {
        executor.run(TenantScope.platformAdmin(), () -> {
            for (UUID id : tenants.idsPastTrial()) {
                tenants.markBilling(id, "PAST_DUE");
            }
            for (UUID id : tenants.idsPastGrace()) {
                tenants.markBilling(id, "UNPAID");
                tenants.updateStatus(id, "SUSPENDED");
                log.info("Auto-suspended tenant {} after unpaid trial/grace", id);
            }
        });
    }
}
