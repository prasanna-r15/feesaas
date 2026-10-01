package com.feesaas.customer.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countActive() {
        return jdbc.sql("select count(*) from customers where deleted_at is null and tenant_id = app_tenant_id()")
                .query(Long.class)
                .single();
    }

    public long nextCodeNumber() {
        return jdbc.sql("select next_counter('customer')").query(Long.class).single();
    }

    public UUID insert(
            UUID tenantId,
            String customerCode,
            String fullName,
            String phone,
            String email,
            String notes,
            LocalDate dueDate,
            boolean hasWhatsapp,
            UUID createdBy,
            UUID branchId) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into customers
                    (id, tenant_id, customer_code, full_name, phone, email, notes, due_date, has_whatsapp, created_by, branch_id)
                values
                    (:id, :tenantId, :code, :name, :phone, :email, :notes, :dueDate, :hasWa, :createdBy, :branchId)
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("code", customerCode)
                .param("name", fullName)
                .param("phone", phone, Types.VARCHAR)
                .param("email", email, Types.VARCHAR)
                .param("notes", notes, Types.VARCHAR)
                .param("dueDate", Date.valueOf(dueDate), Types.DATE)
                .param("hasWa", hasWhatsapp)
                .param("createdBy", createdBy)
                .param("branchId", branchId, Types.OTHER)
                .update();
        return id;
    }

    public List<CustomerRow> list(String query, String status) {
        return list(query, status, null);
    }

    public List<CustomerRow> list(String query, String status, UUID branchId) {
        String q = query == null ? "" : query.trim();
        String statusFilter = status == null ? "" : status.trim();
        return jdbc.sql("""
                select c.id, c.customer_code, c.full_name, c.phone, c.email, c.status, c.notes, c.due_date, c.has_whatsapp, c.created_at,
                       p.id as fee_plan_id, p.name as fee_plan_name, c.branch_id, b.name as branch_name
                  from customers c
                  left join customer_fee_plans cfp
                    on cfp.customer_id = c.id and cfp.tenant_id = c.tenant_id and cfp.status = 'ACTIVE'
                  left join fee_plans p on p.id = cfp.fee_plan_id
                  left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                 where c.deleted_at is null
                   and c.tenant_id = app_tenant_id()
                   and (:status = '' or c.status = :status)
                   and (:branchId::uuid is null or c.branch_id = :branchId)
                   and (
                        :q = ''
                        or c.full_name ilike '%' || :q || '%'
                        or coalesce(c.phone, '') ilike '%' || :q || '%'
                        or coalesce(c.email, '') ilike '%' || :q || '%'
                        or c.customer_code ilike '%' || :q || '%'
                        or coalesce(b.name, '') ilike '%' || :q || '%'
                   )
                 order by c.due_date nulls last, c.full_name
                 limit 200
                """)
                .param("status", statusFilter, Types.VARCHAR)
                .param("q", q, Types.VARCHAR)
                .param("branchId", branchId, Types.OTHER)
                .query(this::map)
                .list();
    }

    public Optional<CustomerRow> findById(UUID id) {
        return jdbc.sql("""
                select c.id, c.customer_code, c.full_name, c.phone, c.email, c.status, c.notes, c.due_date, c.has_whatsapp, c.created_at,
                       p.id as fee_plan_id, p.name as fee_plan_name, c.branch_id, b.name as branch_name
                  from customers c
                  left join customer_fee_plans cfp
                    on cfp.customer_id = c.id and cfp.tenant_id = c.tenant_id and cfp.status = 'ACTIVE'
                  left join fee_plans p on p.id = cfp.fee_plan_id
                  left join tenant_branches b on b.id = c.branch_id and b.tenant_id = c.tenant_id and b.deleted_at is null
                 where c.id = :id and c.deleted_at is null and c.tenant_id = app_tenant_id()
                """)
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public boolean phoneTaken(String phone, UUID excludingId) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        if (excludingId == null) {
            return jdbc.sql("""
                    select count(*) from customers
                     where phone = :phone and deleted_at is null and tenant_id = app_tenant_id()
                    """)
                    .param("phone", phone, Types.VARCHAR)
                    .query(Long.class)
                    .single() > 0;
        }
        return jdbc.sql("""
                select count(*) from customers
                 where phone = :phone and deleted_at is null and id <> :id and tenant_id = app_tenant_id()
                """)
                .param("phone", phone, Types.VARCHAR)
                .param("id", excludingId)
                .query(Long.class)
                .single() > 0;
    }

    public boolean emailTaken(String email, UUID excludingId) {
        if (email == null || email.isBlank()) {
            return false;
        }
        if (excludingId == null) {
            return jdbc.sql("""
                    select count(*) from customers
                     where lower(email) = lower(:email) and deleted_at is null and tenant_id = app_tenant_id()
                    """)
                    .param("email", email, Types.VARCHAR)
                    .query(Long.class)
                    .single() > 0;
        }
        return jdbc.sql("""
                select count(*) from customers
                 where lower(email) = lower(:email) and deleted_at is null and id <> :id and tenant_id = app_tenant_id()
                """)
                .param("email", email, Types.VARCHAR)
                .param("id", excludingId)
                .query(Long.class)
                .single() > 0;
    }

    public int update(
            UUID id,
            String fullName,
            String phone,
            String email,
            String status,
            String notes,
            LocalDate dueDate,
            Boolean hasWhatsapp,
            UUID branchId) {
        return jdbc.sql("""
                update customers
                   set full_name = coalesce(:name, full_name),
                       phone = coalesce(:phone, phone),
                       email = coalesce(:email, email),
                       status = coalesce(:status, status),
                       notes = coalesce(:notes, notes),
                       due_date = coalesce(:dueDate, due_date),
                       has_whatsapp = coalesce(:hasWa, has_whatsapp),
                       branch_id = coalesce(:branchId, branch_id),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and deleted_at is null
                """)
                .param("name", fullName, Types.VARCHAR)
                .param("phone", phone, Types.VARCHAR)
                .param("email", email, Types.VARCHAR)
                .param("status", status, Types.VARCHAR)
                .param("notes", notes, Types.VARCHAR)
                .param("dueDate", dueDate == null ? null : Date.valueOf(dueDate), Types.DATE)
                .param("hasWa", hasWhatsapp, Types.BOOLEAN)
                .param("branchId", branchId, Types.OTHER)
                .param("id", id)
                .update();
    }

    public int softDelete(UUID id) {
        return jdbc.sql("""
                update customers
                   set deleted_at = now(),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and deleted_at is null
                """)
                .param("id", id)
                .update();
    }

    private CustomerRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Date due = rs.getDate("due_date");
        return new CustomerRow(
                rs.getObject("id", UUID.class),
                rs.getString("customer_code"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("status"),
                rs.getString("notes"),
                due == null ? null : due.toLocalDate(),
                created == null ? Instant.now() : created.toInstant(),
                rs.getBoolean("has_whatsapp"),
                rs.getObject("fee_plan_id", UUID.class),
                rs.getString("fee_plan_name"),
                rs.getObject("branch_id", UUID.class),
                rs.getString("branch_name"));
    }

    public record CustomerRow(
            UUID id,
            String customerCode,
            String fullName,
            String phone,
            String email,
            String status,
            String notes,
            LocalDate dueDate,
            Instant createdAt,
            boolean hasWhatsapp,
            UUID feePlanId,
            String feePlanName,
            UUID branchId,
            String branchName
    ) {}
}
