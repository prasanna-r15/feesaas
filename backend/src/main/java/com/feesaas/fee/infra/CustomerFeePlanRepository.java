package com.feesaas.fee.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerFeePlanRepository {

    private final JdbcClient jdbc;

    public CustomerFeePlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ActiveEnrollment> findActive(UUID customerId) {
        return jdbc.sql("""
                select id, fee_plan_id from customer_fee_plans
                 where customer_id = :customerId and status = 'ACTIVE'
                """)
                .param("customerId", customerId)
                .query((rs, i) -> new ActiveEnrollment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("fee_plan_id", UUID.class)))
                .optional();
    }

    public Optional<UUID> findActiveId(UUID customerId) {
        return findActive(customerId).map(ActiveEnrollment::id);
    }

    public void end(UUID id) {
        jdbc.sql("""
                update customer_fee_plans
                   set status = 'ENDED', billing_end = current_date, updated_at = now(), version = version + 1
                 where id = :id and status = 'ACTIVE'
                """)
                .param("id", id)
                .update();
    }

    public UUID insert(UUID tenantId, UUID customerId, UUID planId, LocalDate billingStart) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into customer_fee_plans
                    (id, tenant_id, customer_id, fee_plan_id, billing_start, status)
                values
                    (:id, :tenantId, :customerId, :planId, :start, 'ACTIVE')
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("customerId", customerId)
                .param("planId", planId)
                .param("start", Date.valueOf(billingStart))
                .update();
        return id;
    }

    public List<CustomerDue> customersNeedingEnrollment(UUID tenantId) {
        return jdbc.sql("""
                select c.id, coalesce(c.due_date, current_date + 14) as due_date
                  from customers c
                 where c.tenant_id = :tenantId
                   and c.deleted_at is null
                   and c.status = 'ACTIVE'
                   and not exists (
                        select 1 from customer_fee_plans p
                         where p.customer_id = c.id
                           and p.tenant_id = c.tenant_id
                           and p.status = 'ACTIVE'
                   )
                """)
                .param("tenantId", tenantId)
                .query((rs, i) -> new CustomerDue(
                        rs.getObject("id", UUID.class),
                        rs.getDate("due_date").toLocalDate()))
                .list();
    }

    public List<CustomerDue> customersNeedingCurrentFee(UUID tenantId) {
        return jdbc.sql("""
                select c.id, coalesce(c.due_date, current_date + 14) as due_date
                  from customers c
                  join customer_fee_plans p
                    on p.customer_id = c.id and p.tenant_id = c.tenant_id and p.status = 'ACTIVE'
                 where c.tenant_id = :tenantId
                   and c.deleted_at is null
                   and c.status = 'ACTIVE'
                   and not exists (
                        select 1 from fees f
                         where f.customer_fee_plan_id = p.id
                           and f.tenant_id = c.tenant_id
                           and f.deleted_at is null
                           and f.status in ('PENDING','PARTIALLY_PAID')
                   )
                """)
                .param("tenantId", tenantId)
                .query((rs, i) -> new CustomerDue(
                        rs.getObject("id", UUID.class),
                        rs.getDate("due_date").toLocalDate()))
                .list();
    }

    public boolean customerInTenant(UUID tenantId, UUID customerId) {
        return jdbc.sql("""
                select 1 from customers
                 where id = :customerId and tenant_id = :tenantId and deleted_at is null
                """)
                .param("customerId", customerId)
                .param("tenantId", tenantId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public record CustomerDue(UUID customerId, LocalDate dueDate) {}

    public record ActiveEnrollment(UUID id, UUID planId) {}
}
