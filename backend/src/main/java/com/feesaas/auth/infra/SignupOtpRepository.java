package com.feesaas.auth.infra;

import com.feesaas.shared.persistence.JdbcTimes;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SignupOtpRepository {

    private final JdbcClient jdbc;

    public SignupOtpRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID id,
            String fullName,
            String email,
            String phone,
            String passwordHash,
            String otpHash,
            String channel,
            Instant expiresAt) {
        jdbc.sql("""
                insert into signup_otp_challenges
                  (id, full_name, email, phone, password_hash, otp_hash, channel, expires_at)
                values (:id, :name, :email, :phone, :hash, :otp, :channel, :exp)
                """)
                .param("id", id)
                .param("name", fullName)
                .param("email", email)
                .param("phone", phone)
                .param("hash", passwordHash)
                .param("otp", otpHash)
                .param("channel", channel)
                .param("exp", JdbcTimes.ts(expiresAt))
                .update();
    }

    public Optional<Challenge> find(UUID id) {
        return jdbc.sql("""
                select id, full_name, email, phone, password_hash, otp_hash, channel, attempts, expires_at, used_at
                  from signup_otp_challenges
                 where id = :id
                """)
                .param("id", id)
                .query((rs, i) -> new Challenge(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("password_hash"),
                        rs.getString("otp_hash"),
                        rs.getString("channel"),
                        rs.getInt("attempts"),
                        rs.getTimestamp("expires_at").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("used_at")).map(t -> t.toInstant()).orElse(null)))
                .optional();
    }

    public void bumpAttempts(UUID id) {
        jdbc.sql("update signup_otp_challenges set attempts = attempts + 1 where id = :id")
                .param("id", id)
                .update();
    }

    public void markUsed(UUID id) {
        jdbc.sql("update signup_otp_challenges set used_at = now() where id = :id")
                .param("id", id)
                .update();
    }

    public record Challenge(
            UUID id,
            String fullName,
            String email,
            String phone,
            String passwordHash,
            String otpHash,
            String channel,
            int attempts,
            Instant expiresAt,
            Instant usedAt
    ) {}
}
