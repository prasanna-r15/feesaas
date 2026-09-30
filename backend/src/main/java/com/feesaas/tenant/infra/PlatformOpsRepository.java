package com.feesaas.tenant.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformOpsRepository {

    private final JdbcClient jdbc;

    public PlatformOpsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void audit(UUID tenantId, UUID userId, String action, String entityType, String entityId, String detail) {
        jdbc.sql("""
                insert into audit_logs (id, tenant_id, user_id, action, entity_type, entity_id, new_value)
                values (:id, :tenant, :user, :action, :type, :entity, cast(:detail as jsonb))
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch())
                .param("tenant", tenantId)
                .param("user", userId)
                .param("action", action)
                .param("type", entityType)
                .param("entity", entityId)
                .param("detail", detail == null ? "{}" : "{\"message\":" + toJsonString(detail) + "}")
                .update();
    }

    public List<AuditRow> listAudit(UUID tenantId) {
        return jdbc.sql("""
                select id, user_id, action, entity_type, entity_id, created_at
                  from audit_logs
                 where tenant_id = :id
                 order by created_at desc
                 limit 100
                """)
                .param("id", tenantId)
                .query((rs, i) -> new AuditRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public UUID insertNote(UUID tenantId, UUID createdBy, String body) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into tenant_notes (id, tenant_id, created_by, body)
                values (:id, :tenant, :user, :body)
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("user", createdBy)
                .param("body", body)
                .update();
        return id;
    }

    public List<NoteRow> listNotes(UUID tenantId) {
        return jdbc.sql("""
                select n.id, n.body, n.created_by, u.full_name as author, n.created_at
                  from tenant_notes n
                  left join users u on u.id = n.created_by
                 where n.tenant_id = :id
                 order by n.created_at desc
                 limit 100
                """)
                .param("id", tenantId)
                .query((rs, i) -> new NoteRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("body"),
                        rs.getObject("created_by", UUID.class),
                        rs.getString("author"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public UUID insertInvoice(UUID tenantId, String period, long amountMinor, String currency, LocalDate dueOn, String note) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into platform_invoices (id, tenant_id, period_label, amount_minor, currency, due_on, note)
                values (:id, :tenant, :period, :amount, :currency, :due, :note)
                """)
                .param("id", id)
                .param("tenant", tenantId)
                .param("period", period)
                .param("amount", amountMinor)
                .param("currency", currency)
                .param("due", dueOn == null ? null : java.sql.Date.valueOf(dueOn), java.sql.Types.DATE)
                .param("note", note, java.sql.Types.VARCHAR)
                .update();
        return id;
    }

    public int markInvoice(UUID invoiceId, String status) {
        if ("PAID".equals(status)) {
            return jdbc.sql("""
                    update platform_invoices
                       set status = 'PAID', paid_at = now()
                     where id = :id and status = 'DUE'
                    """)
                    .param("id", invoiceId)
                    .update();
        }
        return jdbc.sql("""
                update platform_invoices set status = 'VOID' where id = :id and status = 'DUE'
                """)
                .param("id", invoiceId)
                .update();
    }

    public List<InvoiceRow> listInvoices(UUID tenantId) {
        return jdbc.sql("""
                select id, period_label, amount_minor, currency, status, due_on, paid_at, note, created_at
                  from platform_invoices
                 where tenant_id = :id
                 order by created_at desc
                """)
                .param("id", tenantId)
                .query((rs, i) -> {
                    java.sql.Date due = rs.getDate("due_on");
                    java.sql.Timestamp paid = rs.getTimestamp("paid_at");
                    return new InvoiceRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("period_label"),
                            rs.getLong("amount_minor"),
                            rs.getString("currency"),
                            rs.getString("status"),
                            due == null ? null : due.toLocalDate(),
                            paid == null ? null : paid.toInstant(),
                            rs.getString("note"),
                            rs.getTimestamp("created_at").toInstant());
                })
                .list();
    }

    public int updateOwner(UUID tenantId, String fullName, String email, String phone) {
        return jdbc.sql("""
                update users
                   set full_name = coalesce(:name, full_name),
                       email = coalesce(:email, email),
                       phone = coalesce(:phone, phone),
                       updated_at = now(),
                       version = version + 1
                 where tenant_id = :tenant and role_code = 'BUSINESS_OWNER'
                """)
                .param("name", fullName)
                .param("email", email)
                .param("phone", phone)
                .param("tenant", tenantId)
                .update();
    }

    public int setOwnerPassword(UUID tenantId, String passwordHash) {
        return jdbc.sql("""
                update users
                   set password_hash = :hash,
                       token_version = token_version + 1,
                       status = 'ACTIVE',
                       updated_at = now()
                 where tenant_id = :tenant and role_code = 'BUSINESS_OWNER'
                """)
                .param("hash", passwordHash)
                .param("tenant", tenantId)
                .update();
    }

    public UUID ownerUserId(UUID tenantId) {
        return jdbc.sql("""
                select id from users
                 where tenant_id = :tenant and role_code = 'BUSINESS_OWNER'
                 order by created_at
                 limit 1
                """)
                .param("tenant", tenantId)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private static String toJsonString(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public record AuditRow(UUID id, UUID userId, String action, String entityType, String entityId, Instant createdAt) {}

    public record NoteRow(UUID id, String body, UUID createdBy, String author, Instant createdAt) {}

    public record InvoiceRow(
            UUID id,
            String periodLabel,
            long amountMinor,
            String currency,
            String status,
            LocalDate dueOn,
            Instant paidAt,
            String note,
            Instant createdAt
    ) {}
}
