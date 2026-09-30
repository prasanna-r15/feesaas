package com.feesaas.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feesaas.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TenantIsolationIT extends AbstractPostgresIT {

    static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Autowired TenantExecutor tenants;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void seedAsOwner() {
        JdbcTemplate owner = ownerJdbc();
        for (UUID id : new UUID[] {A, B}) {
            owner.update("insert into tenants(id, name, slug, business_type) values (?, ?, ?, 'GYM') on conflict do nothing",
                    id, "Tenant " + id, "t-" + id);
            owner.update("insert into tenant_settings(tenant_id) values (?) on conflict do nothing", id);
        }
    }

    @Test
    void appRoleIsNotAllowedToBypassRls() {
        var row = jdbc.sql("select rolsuper, rolbypassrls from pg_roles where rolname = current_user")
                .query().singleRow();
        assertThat(row.get("rolsuper")).isEqualTo(false);
        assertThat(row.get("rolbypassrls")).isEqualTo(false);
    }

    @Test
    void tenantSeesOnlyItsOwnRows() {
        UUID seen = tenants.call(TenantScope.tenant(A), () ->
                jdbc.sql("select tenant_id from tenant_settings").query(UUID.class).single());
        assertThat(seen).isEqualTo(A);

        Long tenantRows = tenants.call(TenantScope.tenant(A), () ->
                jdbc.sql("select count(*) from tenants").query(Long.class).single());
        assertThat(tenantRows).isEqualTo(1L);
    }

    @Test
    void noScopeMeansNoData() {
        Long count = new TransactionTemplate(txManager).execute(s ->
                jdbc.sql("select count(*) from tenant_settings").query(Long.class).single());
        assertThat(count).isZero();
    }

    @Test
    void tenantCannotWriteRowsForAnotherTenant() {
        assertThatThrownBy(() -> tenants.run(TenantScope.tenant(A), () ->
                jdbc.sql("insert into tenant_modules(tenant_id, module_code, enabled) values (:t, 'CUSTOMERS', true)")
                        .param("t", B).update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("row-level security");
    }

    @Test
    void tenantCannotUpdateAnotherTenantsRows() {
        int updated = tenants.call(TenantScope.tenant(A), () ->
                jdbc.sql("update tenant_settings set city = 'HACKED' where tenant_id = :t").param("t", B).update());
        assertThat(updated).isZero();
    }

    @Test
    void platformScopeSeesAllTenants() {
        Long count = tenants.call(TenantScope.platformAdmin(), () ->
                jdbc.sql("select count(*) from tenants where id in (:a, :b)").param("a", A).param("b", B)
                        .query(Long.class).single());
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void scopeDoesNotLeakBetweenPooledConnections() {
        tenants.run(TenantScope.tenant(A), () -> jdbc.sql("select 1").query(Integer.class).single());
        Long count = new TransactionTemplate(txManager).execute(s ->
                jdbc.sql("select count(*) from tenant_settings").query(Long.class).single());
        assertThat(count).isZero();
    }

    @Test
    void auditLogIsAppendOnly() {
        tenants.run(TenantScope.tenant(A), () -> jdbc.sql(
                "insert into audit_logs(tenant_id, action, entity_type) values (:t, 'TEST', 'X')")
                .param("t", A).update());

        assertThatThrownBy(() -> tenants.run(TenantScope.tenant(A), () ->
                jdbc.sql("update audit_logs set action = 'TAMPERED' where tenant_id = :t").param("t", A).update()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("permission denied");
        assertThatThrownBy(() -> tenants.run(TenantScope.tenant(A), () ->
                jdbc.sql("delete from audit_logs where tenant_id = :t").param("t", A).update()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("permission denied");
    }

    @Test
    void perTenantCountersAreSequentialAndIndependent() {
        String key = "receipt-" + UUID.randomUUID();
        long a1 = tenants.call(TenantScope.tenant(A), () -> jdbc.sql("select next_counter(:k)").param("k", key).query(Long.class).single());
        long a2 = tenants.call(TenantScope.tenant(A), () -> jdbc.sql("select next_counter(:k)").param("k", key).query(Long.class).single());
        long b1 = tenants.call(TenantScope.tenant(B), () -> jdbc.sql("select next_counter(:k)").param("k", key).query(Long.class).single());
        assertThat(a1).isEqualTo(1L);
        assertThat(a2).isEqualTo(2L);
        assertThat(b1).isEqualTo(1L);
    }

    @Test
    void loginLookupFunctionIsTheOnlyDoorAroundRls() {
        JdbcTemplate owner = ownerJdbc();
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        owner.update("insert into users(tenant_id, email, full_name, password_hash, role_code) values (?, ?, 'Owner', 'x', 'BUSINESS_OWNER')", A, email);

        Long direct = new TransactionTemplate(txManager).execute(s ->
                jdbc.sql("select count(*) from users where email = :e").param("e", email).query(Long.class).single());
        assertThat(direct).isZero();
        UUID tenant = new TransactionTemplate(txManager).execute(s ->
                jdbc.sql("select tenant_id from auth_find_user(:e)").param("e", email).query(UUID.class).single());
        assertThat(tenant).isEqualTo(A);
    }

    @Test
    void customerRowsAreIsolatedBetweenTenants() {
        tenants.run(TenantScope.tenant(A), () -> jdbc.sql("""
                insert into customers (id, tenant_id, customer_code, full_name, phone)
                values (:id, :t, 'C0001', 'A Member', '+910000000001')
                """)
                .param("id", UUID.randomUUID())
                .param("t", A)
                .update());

        Long seenByB = tenants.call(TenantScope.tenant(B), () ->
                jdbc.sql("select count(*) from customers").query(Long.class).single());
        assertThat(seenByB).isZero();

        assertThatThrownBy(() -> tenants.run(TenantScope.tenant(B), () ->
                jdbc.sql("""
                        insert into customers (id, tenant_id, customer_code, full_name, phone)
                        values (:id, :t, 'HACK', 'Nope', '+910000000002')
                        """)
                        .param("id", UUID.randomUUID())
                        .param("t", A)
                        .update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("row-level security");
    }
}
