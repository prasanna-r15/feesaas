package com.feesaas.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.hibernate.UnknownFilterException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The enforcement point. At the start of EVERY transaction it
 *   1. tells PostgreSQL who is acting (set_config(..., is_local => true) == SET LOCAL),
 *      which activates the row-level-security policies, and
 *   2. enables the Hibernate tenant filter for JPA queries (second layer).
 * Because the setting is transaction-local it can never leak between pooled connections.
 * If no scope is present, RLS returns no rows and rejects writes.
 *
 * Note: a joined (participating) transaction keeps the scope of the outer one.
 * To switch scope use TenantExecutor, which always starts a NEW transaction.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    static final String FILTER = "tenantFilter";

    public TenantAwareTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        try {
            TenantScope scope = TenantContext.current().orElse(TenantScope.NONE);
            applyDatabaseScope(scope);
            applyHibernateFilter(scope);
        } catch (RuntimeException e) {
            doCleanupAfterCompletion(transaction);   // don't leak the half-started transaction
            throw e;
        }
    }

    private void applyDatabaseScope(TenantScope scope) {
        DataSource ds = getDataSource();
        if (ds == null) {
            throw new IllegalStateException("TenantAwareTransactionManager requires a JDBC DataSource");
        }
        Connection con = DataSourceUtils.getConnection(ds);
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true), set_config('app.is_platform', ?, true), set_config('app.user_id', ?, true)")) {
            ps.setString(1, scope.tenantId() == null ? "" : scope.tenantId().toString());
            ps.setString(2, scope.platform() ? "on" : "off");
            ps.setString(3, scope.userId() == null ? "" : scope.userId().toString());
            ps.execute();
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Could not apply tenant scope to the connection", e);
        } finally {
            DataSourceUtils.releaseConnection(con, ds);
        }
    }

    private void applyHibernateFilter(TenantScope scope) {
        if (scope.tenantId() == null) return;
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) return;
        Session session = holder.getEntityManager().unwrap(Session.class);
        try {
            session.enableFilter(FILTER).setParameter("tenantId", scope.tenantId());
        } catch (UnknownFilterException e) {
            // JDBC/RLS modules have no @Entity yet, so the filter is not registered.
        }
    }
}
