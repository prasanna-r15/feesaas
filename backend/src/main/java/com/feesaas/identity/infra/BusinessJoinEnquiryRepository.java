package com.feesaas.identity.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessJoinEnquiryRepository {

    private final JdbcClient jdbc;

    public BusinessJoinEnquiryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(
            UUID userId,
            String fullName,
            String email,
            String phone,
            String businessName,
            String city,
            String message) {
        UUID id = java.util.UUID.randomUUID();
        jdbc.sql("""
                insert into business_join_enquiries
                  (id, user_id, full_name, email, phone, business_name, city, message, status)
                values (:id, :userId, :name, :email, :phone, :biz, :city, :msg, 'OPEN')
                """)
                .param("id", id)
                .param("userId", userId)
                .param("name", fullName)
                .param("email", email)
                .param("phone", phone)
                .param("biz", businessName)
                .param("city", city)
                .param("msg", message)
                .update();
        return id;
    }

    public java.util.Optional<Row> findById(UUID id) {
        return jdbc.sql("""
                select id, user_id, full_name, email, phone, business_name, city, message, status, created_at
                  from business_join_enquiries
                 where id = :id
                """)
                .param("id", id)
                .query((rs, i) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("business_name"),
                        rs.getString("city"),
                        rs.getString("message"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    public void markClosed(UUID id) {
        jdbc.sql("update business_join_enquiries set status = 'CLOSED' where id = :id")
                .param("id", id)
                .update();
    }

    public void markEmailed(UUID id) {
        jdbc.sql("update business_join_enquiries set status = 'EMAILED' where id = :id")
                .param("id", id)
                .update();
    }

    public List<Row> listByUser(UUID userId) {
        return jdbc.sql("""
                select id, user_id, full_name, email, phone, business_name, city, message, status, created_at
                  from business_join_enquiries
                 where user_id = :uid
                 order by created_at desc
                 limit 50
                """)
                .param("uid", userId)
                .query((rs, i) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("business_name"),
                        rs.getString("city"),
                        rs.getString("message"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public List<Row> list() {
        return jdbc.sql("""
                select id, user_id, full_name, email, phone, business_name, city, message, status, created_at
                  from business_join_enquiries
                 order by created_at desc
                 limit 200
                """)
                .query((rs, i) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("business_name"),
                        rs.getString("city"),
                        rs.getString("message"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public record Row(
            UUID id,
            UUID userId,
            String fullName,
            String email,
            String phone,
            String businessName,
            String city,
            String message,
            String status,
            Instant createdAt
    ) {}
}
