package com.feesaas.fee.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeeRepository {

    private final JdbcClient jdbc;

    public FeeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertIgnoreDuplicate(
            UUID tenantId,
            UUID customerId,
            UUID customerFeePlanId,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate dueDate,
            int graceDays,
            long grossMinor,
            String currency) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into fees
                    (id, tenant_id, customer_id, customer_fee_plan_id, period_start, period_end,
                     due_date, grace_days, gross_minor, currency, status)
                values
                    (:id, :tenantId, :customerId, :cfpId, :periodStart, :periodEnd,
                     :dueDate, :grace, :gross, :currency, 'PENDING')
                on conflict (customer_fee_plan_id, period_start) do nothing
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("customerId", customerId)
                .param("cfpId", customerFeePlanId)
                .param("periodStart", Date.valueOf(periodStart))
                .param("periodEnd", Date.valueOf(periodEnd))
                .param("dueDate", Date.valueOf(dueDate))
                .param("grace", graceDays)
                .param("gross", grossMinor)
                .param("currency", currency)
                .update();
    }

    public void updateOpenDueDate(UUID customerId, LocalDate dueDate) {
        jdbc.sql("""
                update fees
                   set due_date = :dueDate, updated_at = now(), version = version + 1
                 where customer_id = :customerId
                   and deleted_at is null
                   and status in ('PENDING','PARTIALLY_PAID')
                """)
                .param("dueDate", Date.valueOf(dueDate))
                .param("customerId", customerId)
                .update();
    }

    public void cancelUnpaidOpen(UUID customerFeePlanId) {
        jdbc.sql("""
                update fees
                   set status = 'CANCELLED', updated_at = now(), version = version + 1
                 where customer_fee_plan_id = :cfpId
                   and deleted_at is null
                   and status = 'PENDING'
                   and paid_minor = 0
                """)
                .param("cfpId", customerFeePlanId)
                .update();
    }

    public List<PendingRow> listPending(String bucket) {
        return listPending(bucket, null);
    }

    public List<PendingRow> listPending(String bucket, UUID branchId) {
        String b = bucket == null ? "" : bucket.trim();
        return jdbc.sql("""
                select f.id, f.customer_id, c.full_name, c.customer_code, c.phone, c.has_whatsapp,
                       f.due_date, f.gross_minor, f.adjustments_minor, f.paid_minor, f.currency,
                       f.status, p.name as plan_name, f.grace_days, c.branch_id, b.name as branch_name
                  from fees f
                  join customers c on c.id = f.customer_id and c.tenant_id = f.tenant_id
                  join customer_fee_plans cfp on cfp.id = f.customer_fee_plan_id
                  join fee_plans p on p.id = cfp.fee_plan_id
                  left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                 where f.deleted_at is null
                   and c.deleted_at is null
                   and f.status in ('PENDING','PARTIALLY_PAID')
                   and (:branchId::uuid is null or c.branch_id = :branchId)
                   and (
                        :bucket = ''
                        or (:bucket = 'overdue' and f.due_date + f.grace_days < current_date)
                        or (:bucket = 'today' and f.due_date = current_date)
                        or (:bucket = 'week' and f.due_date between current_date and current_date + 6)
                        or (:bucket = 'month'
                            and f.due_date >= date_trunc('month', current_date)::date
                            and f.due_date < (date_trunc('month', current_date) + interval '1 month')::date)
                   )
                 order by f.due_date, c.full_name
                 limit 500
                """)
                .param("bucket", b, Types.VARCHAR)
                .param("branchId", branchId, Types.OTHER)
                .query(this::mapPending)
                .list();
    }

    public Optional<RemindRow> findForRemind(UUID id) {
        return jdbc.sql("""
                select f.id, c.full_name, c.phone, c.has_whatsapp, f.due_date,
                       f.gross_minor, f.adjustments_minor, f.paid_minor, f.currency, p.name as plan_name
                  from fees f
                  join customers c on c.id = f.customer_id and c.tenant_id = f.tenant_id
                  join customer_fee_plans cfp on cfp.id = f.customer_fee_plan_id
                  join fee_plans p on p.id = cfp.fee_plan_id
                 where f.id = :id and f.deleted_at is null and c.deleted_at is null
                """)
                .param("id", id)
                .query((rs, i) -> new RemindRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getBoolean("has_whatsapp"),
                        rs.getDate("due_date").toLocalDate(),
                        rs.getLong("gross_minor") + rs.getLong("adjustments_minor") - rs.getLong("paid_minor"),
                        rs.getString("currency").trim(),
                        rs.getString("plan_name")))
                .optional();
    }

    public Optional<LockedFee> lockById(UUID id) {
        return jdbc.sql("""
                select id, customer_id, gross_minor, adjustments_minor, paid_minor, currency, status, version
                  from fees
                 where id = :id and deleted_at is null
                 for update
                """)
                .param("id", id)
                .query((rs, i) -> new LockedFee(
                        rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getLong("gross_minor"),
                        rs.getLong("adjustments_minor"),
                        rs.getLong("paid_minor"),
                        rs.getString("currency").trim(),
                        rs.getString("status"),
                        rs.getLong("version")))
                .optional();
    }

    public int applyPayment(UUID id, long paidMinor, String status, long expectedVersion) {
        return jdbc.sql("""
                update fees
                   set paid_minor = :paid,
                       status = :status,
                       updated_at = now(),
                       version = version + 1
                 where id = :id and version = :version and deleted_at is null
                """)
                .param("paid", paidMinor)
                .param("status", status)
                .param("id", id)
                .param("version", expectedVersion)
                .update();
    }

    public SummaryRow summary() {
        return jdbc.sql("""
                select
                  count(*)::bigint as all_count,
                  coalesce(sum(outstanding), 0)::bigint as all_minor,
                  count(*) filter (where overdue)::bigint as overdue_count,
                  coalesce(sum(outstanding) filter (where overdue), 0)::bigint as overdue_minor,
                  count(*) filter (where due_on = current_date)::bigint as today_count,
                  coalesce(sum(outstanding) filter (where due_on = current_date), 0)::bigint as today_minor
                  from (
                    select (f.gross_minor + f.adjustments_minor - f.paid_minor) as outstanding,
                           (f.due_date + f.grace_days < current_date) as overdue,
                           f.due_date as due_on
                      from fees f
                      join customers c on c.id = f.customer_id and c.tenant_id = f.tenant_id
                     where f.deleted_at is null
                       and c.deleted_at is null
                       and f.status in ('PENDING','PARTIALLY_PAID')
                  ) x
                """)
                .query((rs, i) -> new SummaryRow(
                        rs.getLong("all_count"),
                        rs.getLong("all_minor"),
                        rs.getLong("overdue_count"),
                        rs.getLong("overdue_minor"),
                        rs.getLong("today_count"),
                        rs.getLong("today_minor")))
                .single();
    }

    private PendingRow mapPending(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long gross = rs.getLong("gross_minor");
        long adj = rs.getLong("adjustments_minor");
        long paid = rs.getLong("paid_minor");
        LocalDate due = rs.getDate("due_date").toLocalDate();
        int grace = rs.getInt("grace_days");
        String stored = rs.getString("status");
        boolean overdue = due.plusDays(grace).isBefore(LocalDate.now());
        String effective = overdue && ("PENDING".equals(stored) || "PARTIALLY_PAID".equals(stored))
                ? "OVERDUE"
                : stored;
        return new PendingRow(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getString("full_name"),
                rs.getString("customer_code"),
                rs.getString("phone"),
                rs.getBoolean("has_whatsapp"),
                due,
                gross,
                paid,
                gross + adj - paid,
                rs.getString("currency").trim(),
                stored,
                effective,
                rs.getString("plan_name"),
                rs.getObject("branch_id", UUID.class),
                rs.getString("branch_name"));
    }

    public record PendingRow(
            UUID id,
            UUID customerId,
            String customerName,
            String customerCode,
            String phone,
            boolean hasWhatsapp,
            LocalDate dueDate,
            long grossMinor,
            long paidMinor,
            long outstandingMinor,
            String currency,
            String status,
            String effectiveStatus,
            String planName,
            UUID branchId,
            String branchName
    ) {}

    public record RemindRow(
            UUID id,
            String customerName,
            String phone,
            boolean hasWhatsapp,
            LocalDate dueDate,
            long outstandingMinor,
            String currency,
            String planName
    ) {}

    public record LockedFee(
            UUID id,
            UUID customerId,
            long grossMinor,
            long adjustmentsMinor,
            long paidMinor,
            String currency,
            String status,
            long version
    ) {
        public long outstanding() {
            return grossMinor + adjustmentsMinor - paidMinor;
        }
    }

    public record SummaryRow(
            long allCount,
            long allMinor,
            long overdueCount,
            long overdueMinor,
            long todayCount,
            long todayMinor
    ) {}
}
