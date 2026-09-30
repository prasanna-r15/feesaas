package com.feesaas.shared.tenancy;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work as a given scope in its own transaction. Used by schedulers (one transaction per tenant, so a
 * failing tenant can't affect others), by login (after the user is identified), and by platform admins
 * acting on a specific tenant.
 */
@Component
public class TenantExecutor {

    private final TransactionTemplate tx;

    public TenantExecutor(PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T call(TenantScope scope, Supplier<T> work) {
        // scope is set BEFORE the transaction starts so doBegin() sees it
        return TenantContext.callAs(scope, () -> tx.execute(status -> work.get()));
    }

    public void run(TenantScope scope, Runnable work) {
        call(scope, () -> { work.run(); return null; });
    }
}
