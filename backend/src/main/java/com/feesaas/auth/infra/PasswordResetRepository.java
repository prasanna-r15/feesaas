package com.feesaas.auth.infra;

import com.feesaas.shared.persistence.JdbcTimes;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetRepository {

    private final JdbcClient jdbc;

    public PasswordResetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void invalidateUnused(UUID userId) {
        jdbc.sql("update password_reset_tokens set used_at = now() where user_id = :userId and used_at is null")
                .param("userId", userId)
                .update();
    }

    public UUID insert(UUID userId, String tokenHash, Instant expiresAt) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into password_reset_tokens (id, user_id, token_hash, expires_at)
                values (:id, :userId, :hash, :expiresAt)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("hash", tokenHash)
                .param("expiresAt", JdbcTimes.ts(expiresAt))
                .update();
        return id;
    }

    public Optional<ResetToken> findActive(String tokenHash) {
        return jdbc.sql("""
                select id, user_id
                  from password_reset_tokens
                 where token_hash = :hash
                   and used_at is null
                   and expires_at > now()
                """)
                .param("hash", tokenHash)
                .query((rs, i) -> new ResetToken(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class)))
                .optional();
    }

    public Optional<ResetToken> findActive(UUID id, String tokenHash) {
        if (id == null) {
            return findActive(tokenHash);
        }
        return jdbc.sql("""
                select id, user_id
                  from password_reset_tokens
                 where id = :id
                   and token_hash = :hash
                   and used_at is null
                   and expires_at > now()
                """)
                .param("id", id)
                .param("hash", tokenHash)
                .query((rs, i) -> new ResetToken(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class)))
                .optional();
    }

    public void markUsed(UUID id) {
        jdbc.sql("update password_reset_tokens set used_at = now() where id = :id")
                .param("id", id)
                .update();
    }

    public record ResetToken(UUID id, UUID userId) {}
}
