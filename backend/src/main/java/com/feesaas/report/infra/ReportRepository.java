package com.feesaas.report.infra;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PeriodRow> collectionPeriods() {
        return jdbc.sql("""
                select extract(year from d)::int as y, extract(month from d)::int as m
                  from (
                    select paid_on as d from payments where status = 'RECORDED' and tenant_id = app_tenant_id()
                    union
                    select sold_on from addon_sales where tenant_id = app_tenant_id()
                  ) x
                 group by 1, 2
                 order by y desc, m desc
                """)
                .query((rs, i) -> new PeriodRow(rs.getInt("y"), rs.getInt("m")))
                .list();
    }

    public OverviewRow overview(LocalDate day, LocalDate monthStart, LocalDate monthEnd) {
        return jdbc.sql("""
                select
                  (select count(*) from customers
                    where deleted_at is null and status = 'ACTIVE' and tenant_id = app_tenant_id()) as members,
                  (select count(*) from fees
                    where deleted_at is null and status in ('PENDING','PARTIALLY_PAID') and tenant_id = app_tenant_id()) as pending_count,
                  (select coalesce(sum(gross_minor + adjustments_minor - paid_minor), 0) from fees
                    where deleted_at is null and status in ('PENDING','PARTIALLY_PAID') and tenant_id = app_tenant_id()) as outstanding_minor,
                  (select count(*) from fees
                    where deleted_at is null and status in ('PENDING','PARTIALLY_PAID')
                      and due_date + grace_days < current_date and tenant_id = app_tenant_id()) as overdue_count,
                  (select coalesce(sum(gross_minor + adjustments_minor - paid_minor), 0) from fees
                    where deleted_at is null and status in ('PENDING','PARTIALLY_PAID')
                      and due_date + grace_days < current_date and tenant_id = app_tenant_id()) as overdue_minor,
                  (select coalesce(sum(amount_minor), 0) from payments
                    where status = 'RECORDED' and paid_on = :day and tenant_id = app_tenant_id()) as fees_today_minor,
                  (select coalesce(sum(amount_minor), 0) from payments
                    where status = 'RECORDED'
                      and paid_on >= :monthStart
                      and paid_on < :monthEnd
                      and tenant_id = app_tenant_id()) as fees_month_minor,
                  (select coalesce(sum(amount_minor), 0) from addon_sales
                    where sold_on = :day and tenant_id = app_tenant_id()) as extras_today_minor,
                  (select coalesce(sum(amount_minor), 0) from addon_sales
                    where sold_on >= :monthStart
                      and sold_on < :monthEnd
                      and tenant_id = app_tenant_id()) as extras_month_minor
                """)
                .param("day", Date.valueOf(day))
                .param("monthStart", Date.valueOf(monthStart))
                .param("monthEnd", Date.valueOf(monthEnd))
                .query((rs, i) -> new OverviewRow(
                        rs.getLong("members"),
                        rs.getLong("pending_count"),
                        rs.getLong("outstanding_minor"),
                        rs.getLong("overdue_count"),
                        rs.getLong("overdue_minor"),
                        rs.getLong("fees_today_minor"),
                        rs.getLong("fees_month_minor"),
                        rs.getLong("extras_today_minor"),
                        rs.getLong("extras_month_minor")))
                .single();
    }

    public List<PlanRow> byPlan() {
        return jdbc.sql("""
                select p.name,
                       count(*)::bigint as fee_count,
                       coalesce(sum(f.gross_minor + f.adjustments_minor - f.paid_minor), 0) as outstanding_minor
                  from fees f
                  join customer_fee_plans cfp on cfp.id = f.customer_fee_plan_id
                  join fee_plans p on p.id = cfp.fee_plan_id
                 where f.deleted_at is null
                   and f.tenant_id = app_tenant_id()
                   and f.status in ('PENDING','PARTIALLY_PAID')
                 group by p.name
                 order by outstanding_minor desc, p.name
                """)
                .query((rs, i) -> new PlanRow(
                        rs.getString("name"),
                        rs.getLong("fee_count"),
                        rs.getLong("outstanding_minor")))
                .list();
    }

    public List<PaymentRow> recentPayments(LocalDate paidOn, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                select * from (
                  select p.receipt_no as receipt_no,
                         c.full_name,
                         p.amount_minor,
                         p.currency,
                         p.method,
                         p.paid_on as paid_on,
                         p.created_at as sort_at,
                         'FEE' as source,
                         coalesce(fp.name, 'Membership') as category,
                         b.name as branch_name
                    from payments p
                    join customers c on c.id = p.customer_id
                    left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                    left join fees f on f.id = (
                      select a.fee_id from payment_allocations a where a.payment_id = p.id order by a.amount_minor desc limit 1)
                    left join customer_fee_plans cfp on cfp.id = f.customer_fee_plan_id
                    left join fee_plans fp on fp.id = cfp.fee_plan_id
                   where p.status = 'RECORDED'
                     and p.tenant_id = app_tenant_id()
                     and (:paidOn::date is null or p.paid_on = :paidOn)
                     and (:from::date is null or p.paid_on >= :from)
                     and (:to::date is null or p.paid_on < :to)
                  union all
                  select coalesce('EXTRA-' || substr(s.id::text, 1, 8), 'EXTRA') as receipt_no,
                         c.full_name,
                         s.amount_minor,
                         s.currency,
                         s.method,
                         s.sold_on as paid_on,
                         s.created_at as sort_at,
                         'EXTRA' as source,
                         pr.name as category,
                         b.name as branch_name
                    from addon_sales s
                    join customers c on c.id = s.customer_id
                    join addon_products pr on pr.id = s.product_id and pr.tenant_id = s.tenant_id
                    left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                   where s.tenant_id = app_tenant_id()
                     and (:paidOn::date is null or s.sold_on = :paidOn)
                     and (:from::date is null or s.sold_on >= :from)
                     and (:to::date is null or s.sold_on < :to)
                ) u
                 order by u.paid_on desc, u.sort_at desc
                 limit 200
                """)
                .param("paidOn", paidOn == null ? null : Date.valueOf(paidOn), Types.DATE)
                .param("from", from == null ? null : Date.valueOf(from), Types.DATE)
                .param("to", to == null ? null : Date.valueOf(to), Types.DATE)
                .query((rs, i) -> new PaymentRow(
                        rs.getString("receipt_no"),
                        rs.getString("full_name"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency").trim(),
                        rs.getString("method"),
                        rs.getDate("paid_on").toLocalDate(),
                        rs.getString("source"),
                        rs.getString("category"),
                        rs.getString("branch_name")))
                .list();
    }

    public List<ExportRow> exportCollections() {
        return jdbc.sql("""
                select * from (
                  select 'FEE' as source,
                         p.receipt_no as receipt_no,
                         c.full_name,
                         c.customer_code,
                         b.name as branch_name,
                         p.amount_minor,
                         p.currency,
                         p.method,
                         coalesce(p.reference_no, '') as reference_no,
                         p.paid_on as paid_on,
                         p.status as status,
                         p.created_at as sort_at
                    from payments p
                    join customers c on c.id = p.customer_id
                    left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                   where p.tenant_id = app_tenant_id()
                  union all
                  select 'EXTRA' as source,
                         coalesce('EXTRA-' || substr(s.id::text, 1, 8), 'EXTRA') as receipt_no,
                         c.full_name,
                         c.customer_code,
                         b.name as branch_name,
                         s.amount_minor,
                         s.currency,
                         s.method,
                         coalesce(pr.name, '') as reference_no,
                         s.sold_on as paid_on,
                         'RECORDED' as status,
                         s.created_at as sort_at
                    from addon_sales s
                    join customers c on c.id = s.customer_id
                    join addon_products pr on pr.id = s.product_id and pr.tenant_id = s.tenant_id
                    left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                   where s.tenant_id = app_tenant_id()
                ) u
                 order by u.paid_on desc, u.sort_at desc
                """)
                .query((rs, i) -> new ExportRow(
                        rs.getString("source"),
                        rs.getString("receipt_no"),
                        rs.getString("full_name"),
                        rs.getString("customer_code"),
                        rs.getString("branch_name"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency").trim(),
                        rs.getString("method"),
                        rs.getString("reference_no"),
                        rs.getDate("paid_on").toLocalDate(),
                        rs.getString("status")))
                .list();
    }

    public record PeriodRow(int year, int month) {}

    public record OverviewRow(
            long members,
            long pendingCount,
            long outstandingMinor,
            long overdueCount,
            long overdueMinor,
            long feesTodayMinor,
            long feesMonthMinor,
            long extrasTodayMinor,
            long extrasMonthMinor
    ) {}

    public record PlanRow(String name, long feeCount, long outstandingMinor) {}

    public record PaymentRow(
            String receiptNo,
            String customerName,
            long amountMinor,
            String currency,
            String method,
            LocalDate paidOn,
            String source,
            String category,
            String branchName
    ) {}

    public record ExportRow(
            String source,
            String receiptNo,
            String customerName,
            String customerCode,
            String branchName,
            long amountMinor,
            String currency,
            String method,
            String referenceNo,
            LocalDate paidOn,
            String status
    ) {}
}
