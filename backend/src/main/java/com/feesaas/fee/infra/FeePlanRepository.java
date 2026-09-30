package com.feesaas.fee.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeePlanRepository {

    public static final long GENERAL_MINOR = 150_000L;
    public static final long CARDIO_MINOR = 250_000L;
    public static final long PT_MINOR = 500_000L;

    private final JdbcClient jdbc;

    public FeePlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void seedStarterPlans(UUID tenantId, String currency) {
        long count = jdbc.sql("select count(*) from fee_plans where deleted_at is null")
                .query(Long.class)
                .single();
        if (count == 0) {
            insert(tenantId, "General", GENERAL_MINOR, currency, "MONTHLY", 0, true);
            insert(tenantId, "Cardio", CARDIO_MINOR, currency, "MONTHLY", 0, false);
            insert(tenantId, "PT", PT_MINOR, currency, "MONTHLY", 0, false);
            return;
        }
        insertIfNameMissing(tenantId, "Cardio", CARDIO_MINOR, currency);
        insertIfNameMissing(tenantId, "PT", PT_MINOR, currency);
        ensureDefault(tenantId, currency);
    }

    public UUID ensureDefault(UUID tenantId, String currency) {
        Optional<UUID> existing = jdbc.sql("""
                select id from fee_plans
                 where tenant_id = :tenantId and is_default and deleted_at is null
                """)
                .param("tenantId", tenantId)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
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
        if (any.isPresent()) {
            jdbc.sql("update fee_plans set is_default = true, updated_at = now() where id = :id")
                    .param("id", any.get())
                    .update();
            return any.get();
        }
        return insert(tenantId, "General", GENERAL_MINOR, currency, "MONTHLY", 0, true);
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
        return jdbc.sql("""
                select id, name, amount_minor, currency, billing_cycle, grace_days, is_default
                  from fee_plans
                 where deleted_at is null
                 order by is_default desc, name
                """)
                .query(this::map)
                .list();
    }

    public Optional<PlanRow> findByName(String name) {
        return jdbc.sql("""
                select id, name, amount_minor, currency, billing_cycle, grace_days, is_default
                  from fee_plans
                 where lower(name) = lower(:name) and deleted_at is null
                """)
                .param("name", name)
                .query(this::map)
                .optional();
    }

    public Optional<PlanRow> findById(UUID id) {
        return jdbc.sql("""
                select id, name, amount_minor, currency, billing_cycle, grace_days, is_default
                  from fee_plans
                 where id = :id and deleted_at is null
                """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public boolean nameTaken(String name, UUID excludingId) {
        if (excludingId == null) {
            return jdbc.sql("""
                    select count(*) from fee_plans
                     where lower(name) = lower(:name) and deleted_at is null
                    """)
                    .param("name", name, Types.VARCHAR)
                    .query(Long.class)
                    .single() > 0;
        }
        return jdbc.sql("""
                select count(*) from fee_plans
                 where lower(name) = lower(:name) and deleted_at is null and id <> :id
                """)
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
                 where id = :id and deleted_at is null
                """)
                .param("name", name, Types.VARCHAR)
                .param("amount", amountMinor, Types.BIGINT)
                .param("cycle", billingCycle, Types.VARCHAR)
                .param("grace", graceDays, Types.INTEGER)
                .param("isDefault", isDefault, Types.BOOLEAN)
                .param("id", id)
                .update();
    }

    public int softDelete(UUID id) {
        return jdbc.sql("""
                update fee_plans
                   set deleted_at = now(), is_default = false, updated_at = now(), version = version + 1
                 where id = :id and deleted_at is null
                """)
                .param("id", id)
                .update();
    }

    public long activeEnrollmentCount(UUID planId) {
        return jdbc.sql("""
                select count(*) from customer_fee_plans
                 where fee_plan_id = :id and status = 'ACTIVE'
                """)
                .param("id", planId)
                .query(Long.class)
                .single();
    }

    public void clearDefault() {
        jdbc.sql("update fee_plans set is_default = false where is_default and deleted_at is null")
                .update();
    }

    private void insertIfNameMissing(UUID tenantId, String name, long amountMinor, String currency) {
        boolean exists = jdbc.sql("""
                select count(*) from fee_plans
                 where lower(name) = lower(:name) and deleted_at is null
                """)
                .param("name", name)
                .query(Long.class)
                .single() > 0;
        if (!exists) {
            insert(tenantId, name, amountMinor, currency, "MONTHLY", 0, false);
        }
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
