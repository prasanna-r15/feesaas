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
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String nextReceiptNo() {
        long n = jdbc.sql("select next_counter('receipt')").query(Long.class).single();
        return "R" + String.format("%06d", n);
    }

    public UUID insert(
            UUID tenantId,
            UUID customerId,
            long amountMinor,
            String currency,
            String method,
            String referenceNo,
            LocalDate paidOn,
            String receiptNo,
            UUID createdBy) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into payments
                    (id, tenant_id, customer_id, amount_minor, currency, method, reference_no,
                     receipt_no, paid_on, status, created_by)
                values
                    (:id, :tenantId, :customerId, :amount, :currency, :method, :ref,
                     :receipt, :paidOn, 'RECORDED', :createdBy)
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("customerId", customerId)
                .param("amount", amountMinor)
                .param("currency", currency)
                .param("method", method)
                .param("ref", referenceNo, Types.VARCHAR)
                .param("receipt", receiptNo)
                .param("paidOn", Date.valueOf(paidOn))
                .param("createdBy", createdBy)
                .update();
        return id;
    }

    public void allocate(UUID tenantId, UUID paymentId, UUID feeId, long amountMinor) {
        jdbc.sql("""
                insert into payment_allocations (id, tenant_id, payment_id, fee_id, amount_minor)
                values (:id, :tenantId, :paymentId, :feeId, :amount)
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch())
                .param("tenantId", tenantId)
                .param("paymentId", paymentId)
                .param("feeId", feeId)
                .param("amount", amountMinor)
                .update();
    }

    public List<PaymentRow> list() {
        return jdbc.sql("""
                select p.id, p.receipt_no, c.full_name, c.customer_code, p.amount_minor, p.currency,
                       p.method, p.reference_no, p.paid_on, p.status, p.void_reason, b.name as branch_name
                  from payments p
                  join customers c on c.id = p.customer_id
                  left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                 where p.tenant_id = app_tenant_id()
                 order by p.created_at desc
                 limit 500
                """)
                .query(this::map)
                .list();
    }

    public Optional<PaymentRow> findById(UUID id) {
        return jdbc.sql("""
                select p.id, p.receipt_no, c.full_name, c.customer_code, p.amount_minor, p.currency,
                       p.method, p.reference_no, p.paid_on, p.status, p.void_reason, b.name as branch_name
                  from payments p
                  join customers c on c.id = p.customer_id
                  left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                 where p.id = :id and p.tenant_id = app_tenant_id()
                """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public int markVoid(UUID id, String reason, UUID voidedBy) {
        return jdbc.sql("""
                update payments
                   set status = 'VOID',
                       void_reason = :reason,
                       voided_at = now(),
                       voided_by = :by
                 where id = :id and status = 'RECORDED' and tenant_id = app_tenant_id()
                """)
                .param("reason", reason)
                .param("by", voidedBy)
                .param("id", id)
                .update();
    }

    public List<AllocationRow> allocations(UUID paymentId) {
        return jdbc.sql("""
                select fee_id, amount_minor
                  from payment_allocations
                 where payment_id = :id
                """)
                .param("id", paymentId)
                .query((rs, i) -> new AllocationRow(
                        rs.getObject("fee_id", UUID.class),
                        rs.getLong("amount_minor")))
                .list();
    }

    public List<PaymentRow> export() {
        return jdbc.sql("""
                select p.id, p.receipt_no, c.full_name, c.customer_code, p.amount_minor, p.currency,
                       p.method, p.reference_no, p.paid_on, p.status, p.void_reason, b.name as branch_name
                  from payments p
                  join customers c on c.id = p.customer_id
                  left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                 where p.tenant_id = app_tenant_id()
                 order by p.paid_on desc, p.created_at desc
                """)
                .query(this::map)
                .list();
    }

    private PaymentRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new PaymentRow(
                rs.getObject("id", UUID.class),
                rs.getString("receipt_no"),
                rs.getString("full_name"),
                rs.getString("customer_code"),
                rs.getLong("amount_minor"),
                rs.getString("currency").trim(),
                rs.getString("method"),
                rs.getString("reference_no"),
                rs.getDate("paid_on").toLocalDate(),
                rs.getString("status"),
                rs.getString("void_reason"),
                rs.getString("branch_name"));
    }

    public record PaymentRow(
            UUID id,
            String receiptNo,
            String customerName,
            String customerCode,
            long amountMinor,
            String currency,
            String method,
            String referenceNo,
            java.time.LocalDate paidOn,
            String status,
            String voidReason,
            String branchName
    ) {}

    public record AllocationRow(UUID feeId, long amountMinor) {}
}
