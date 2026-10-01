package com.feesaas.fee.infra;

import com.feesaas.shared.tenancy.TenantContext;
import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeePlanRepository {

    private final JdbcClient jdbc;

    public FeePlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findDefaultOrAny() {
        UUID tenantId = TenantContext.requireTenantId();
        Optional<UUID> existing = jdbc.sql("""
                select id from fee_plans
                 where tenant_id = :tenantId and is_default and deleted_at is null
                """)
                .param("tenantId", tenantId)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return existing;
        }
        return jdbc.sql("""
                select id from fee_plans
                 where tenant_id = :tenantId and deleted_at is null
                 order by name
                 limit 1
                """)
                .param("tenantId", tenantId)
                .query(UUID.class)
                .optional();
    }

    public void promoteDefaultIfMissing() {
        UUID tenantId = TenantContext.requireTenantId();
        Optional<UUID> existing = jdbc.sql("""
                select id from fee_plans
                 where tenant_id = :tenantId and is_default and deleted_at is null
                """)
                .param("tenantId", tenantId)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return;
        }
        Optional<UUID> any = jdbc.sql("""
                select id from fee_plans
                 where tenant_id = :tenantId and deleted_at is null
                 order by name
                 limit 1
                """)
                .param("tenantId", tenantId)
                .query(UUID.class)
                .optional();
        if (any.isEmpty()) {
            return;
        }
        jdbc.sql("""
                update fee_plans set is_default = true, updated_at = now()
                 where id = :id and tenant_id = :tenantId
                """)
                .param("id", any.get())
                .param("tenantId", tenantId)
                .update();
    }

    public UUID insert(
            UUID tenantId,
            String name,
            long amountMinor,
            String currency,
            String billingCycle,
            int graceDays,
            boolean isDefault) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        if (isDefault) {
            clearDefault();
        }
        jdbc.sql("""
                insert into fee_plans
                    (id, tenant_id, name, amount_minor, currency, billing_cycle, grace_days, is_default)
                values
                    (:id, :tenantId, :name, :amount, :currency, :cycle, :grace, :isDefault)
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name.trim())
                .param("amount", amountMinor)
                .param("currency", currency)
                .param("cycle", billingCycle)
                .param("grace", graceDays)
                .param("isDefault", isDefault)
                .update();
        return id;
    }

    public List<PlanRow> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                select id, name, amount_minor, currency, billing_cycle, grace_days, is_default
                  from fee_plans
                 where deleted_at is null
                   and tenant_id = :tenantId
                 order by is_default desc, name
                """)
                .param("tenantId", tenantId)
                .query(this::map)
                .list();
    }

    public Optional<PlanRow> findByName(String name) {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                select id, name, amount_minor, currency, billing_cycle, grace_days, is_default
                  from fee_plans
                 where tenant_id = :tenantId
                   and lower(name) = lower(:name)
                   and deleted_at is null
                """)
                .param("tenantId", tenantId)
                .param("name", name)
                .query(this::map)
                .optional();
    }

    public Optional<PlanRow> findById(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                select id, name, amount_minor, currency, billing_cycle, grace_days, is_default
                  from fee_plans
                 where id = :id and tenant_id = :tenantId and deleted_at is null
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .query(this::map)
                .optional();
    }

    public boolean nameTaken(String name, UUID excludingId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (excludingId == null) {
            return jdbc.sql("""
                    select count(*) from fee_plans
                     where tenant_id = :tenantId
                       and lower(name) = lower(:name)
                       and deleted_at is null
                    """)
                    .param("tenantId", tenantId)
                    .param("name", name, Types.VARCHAR)
                    .query(Long.class)
                    .single() > 0;
        }
        return jdbc.sql("""
                select count(*) from fee_plans
                 where tenant_id = :tenantId
                   and lower(name) = lower(:name)
                   and deleted_at is null
                   and id <> :id
                """)
                .param("tenantId", tenantId)
                .param("name", name, Types.VARCHAR)
                .param("id", excludingId)
                .query(Long.class)
                .single() > 0;
    }

    public int update(
            UUID id,
            String name,
            Long amountMinor,
            String billingCycle,
            Integer graceDays,
            Boolean isDefault) {
        UUID tenantId = TenantContext.requireTenantId();
        if (Boolean.TRUE.equals(isDefault)) {
            clearDefault();
        }
        return jdbc.sql("""
                update fee_plans
                   set name = coalesce(:name, name),
                       amount_minor = coalesce(:amount, amount_minor),
                       billing_cycle = coalesce(:cycle, billing_cycle),
                       grace_days = coalesce(:grace, grace_days),
                       is_default = coalesce(:isDefault, is_default),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and tenant_id = :tenantId and deleted_at is null
                """)
                .param("name", name, Types.VARCHAR)
                .param("amount", amountMinor, Types.BIGINT)
                .param("cycle", billingCycle, Types.VARCHAR)
                .param("grace", graceDays, Types.INTEGER)
                .param("isDefault", isDefault, Types.BOOLEAN)
                .param("id", id)
                .param("tenantId", tenantId)
                .update();
    }

    public int softDelete(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                update fee_plans
                   set deleted_at = now(), is_default = false, updated_at = now(), version = version + 1
                 where id = :id and tenant_id = :tenantId and deleted_at is null
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .update();
    }

    public long activeEnrollmentCount(UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                select count(*) from customer_fee_plans
                 where fee_plan_id = :id and tenant_id = :tenantId and status = 'ACTIVE'
                """)
                .param("id", planId)
                .param("tenantId", tenantId)
                .query(Long.class)
                .single();
    }

    public void clearDefault() {
        UUID tenantId = TenantContext.requireTenantId();
        jdbc.sql("""
                update fee_plans
                   set is_default = false
                 where tenant_id = :tenantId and is_default and deleted_at is null
                """)
                .param("tenantId", tenantId)
                .update();
    }

    private PlanRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new PlanRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getLong("amount_minor"),
                rs.getString("currency").trim(),
                rs.getString("billing_cycle"),
                rs.getInt("grace_days"),
                rs.getBoolean("is_default"));
    }

    public record PlanRow(
            UUID id,
            String name,
            long amountMinor,
            String currency,
            String billingCycle,
            int graceDays,
            boolean isDefault
    ) {}
}
