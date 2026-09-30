package com.feesaas.attendance.infra;

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
public class BatchRepository {

    private final JdbcClient jdbc;

    public BatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, String name, String schedule) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into batches (id, tenant_id, name, schedule)
                values (:id, :tenantId, :name, :schedule)
                """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name)
                .param("schedule", schedule, Types.VARCHAR)
                .update();
        return id;
    }

    public List<BatchRow> list() {
        return jdbc.sql("""
                select b.id, b.name, b.schedule,
                       (select count(*) from batch_members m where m.batch_id = b.id) as members
                  from batches b
                 where b.deleted_at is null
                 order by b.name
                """)
                .query(this::mapBatch)
                .list();
    }

    public Optional<BatchRow> findById(UUID id) {
        return jdbc.sql("""
                select b.id, b.name, b.schedule,
                       (select count(*) from batch_members m where m.batch_id = b.id) as members
                  from batches b
                 where b.id = :id and b.deleted_at is null
                """)
                .param("id", id)
                .query(this::mapBatch)
                .optional();
    }

    public boolean nameTaken(String name, UUID excluding) {
        if (excluding == null) {
            return jdbc.sql("""
                    select count(*) from batches
                     where lower(name) = lower(:name) and deleted_at is null
                    """)
                    .param("name", name)
                    .query(Long.class)
                    .single() > 0;
        }
        return jdbc.sql("""
                select count(*) from batches
                 where lower(name) = lower(:name) and deleted_at is null and id <> :id
                """)
                .param("name", name)
                .param("id", excluding)
                .query(Long.class)
                .single() > 0;
    }

    public int update(UUID id, String name, String schedule) {
        return jdbc.sql("""
                update batches
                   set name = coalesce(:name, name),
                       schedule = coalesce(:schedule, schedule),
                       updated_at = now(),
                       version = version + 1
                 where id = :id and deleted_at is null
                """)
                .param("name", name, Types.VARCHAR)
                .param("schedule", schedule, Types.VARCHAR)
                .param("id", id)
                .update();
    }

    public int softDelete(UUID id) {
        return jdbc.sql("""
                update batches
                   set deleted_at = now(), updated_at = now(), version = version + 1
                 where id = :id and deleted_at is null
                """)
                .param("id", id)
                .update();
    }

    public void addMember(UUID tenantId, UUID batchId, UUID customerId) {
        jdbc.sql("""
                insert into batch_members (tenant_id, batch_id, customer_id)
                values (:tenantId, :batchId, :customerId)
                on conflict (batch_id, customer_id) do nothing
                """)
                .param("tenantId", tenantId)
                .param("batchId", batchId)
                .param("customerId", customerId)
                .update();
    }

    public int removeMember(UUID batchId, UUID customerId) {
        return jdbc.sql("delete from batch_members where batch_id = :batchId and customer_id = :customerId")
                .param("batchId", batchId)
                .param("customerId", customerId)
                .update();
    }

    public List<MemberRow> members(UUID batchId) {
        return jdbc.sql("""
                select c.id, c.customer_code, c.full_name, c.phone
                  from batch_members m
                  join customers c on c.id = m.customer_id
                 where m.batch_id = :id and c.deleted_at is null
                 order by c.full_name
                """)
                .param("id", batchId)
                .query((rs, i) -> new MemberRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_code"),
                        rs.getString("full_name"),
                        rs.getString("phone")))
                .list();
    }

    public void upsertAttendance(UUID tenantId, UUID batchId, UUID customerId, LocalDate on, String status) {
        jdbc.sql("""
                insert into attendance_marks (id, tenant_id, batch_id, customer_id, marked_on, status)
                values (:id, :tenantId, :batchId, :customerId, :on, :status)
                on conflict (batch_id, customer_id, marked_on)
                do update set status = excluded.status
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch())
                .param("tenantId", tenantId)
                .param("batchId", batchId)
                .param("customerId", customerId)
                .param("on", Date.valueOf(on))
                .param("status", status)
                .update();
    }

    public List<MarkRow> marks(UUID batchId, LocalDate on) {
        return jdbc.sql("""
                select customer_id, status
                  from attendance_marks
                 where batch_id = :batchId and marked_on = :on
                """)
                .param("batchId", batchId)
                .param("on", Date.valueOf(on))
                .query((rs, i) -> new MarkRow(
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("status")))
                .list();
    }

    private BatchRow mapBatch(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new BatchRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("schedule"),
                rs.getLong("members"));
    }

    public record BatchRow(UUID id, String name, String schedule, long memberCount) {}

    public record MemberRow(UUID id, String customerCode, String fullName, String phone) {}

    public record MarkRow(UUID customerId, String status) {}
}
