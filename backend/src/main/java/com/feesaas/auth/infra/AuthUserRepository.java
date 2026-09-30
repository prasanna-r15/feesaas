package com.feesaas.auth.infra;

import com.feesaas.auth.domain.AuthUser;
import com.feesaas.shared.persistence.JdbcTimes;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthUserRepository {

    private final JdbcClient jdbc;

    public AuthUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthUser> findByIdentifier(String identifier) {
        return jdbc.sql("""
                        select id, tenant_id, full_name, password_hash, role_code, status,
                               token_version, failed_login_count, locked_until
                        from auth_find_user(:identifier)
                        """)
                .param("identifier", identifier)
                .query((rs, i) -> new AuthUser(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("password_hash"),
                        rs.getString("role_code"),
                        rs.getString("status"),
                        rs.getInt("token_version"),
                        rs.getInt("failed_login_count"),
                        toInstant(rs.getTimestamp("locked_until"))))
                .optional();
    }

    public Optional<String> tenantStatus(UUID tenantId) {
        return jdbc.sql("select status from tenants where id = :id")
                .param("id", tenantId)
                .query(String.class)
                .optional();
    }

    public void recordFailedLogin(UUID userId, int failedLoginCount, Instant lockedUntil) {
        jdbc.sql("""
                update users
                   set failed_login_count = :fails,
                       locked_until = :locked,
                       updated_at = now()
                 where id = :id
                """)
                .param("fails", failedLoginCount)
                .param("locked", JdbcTimes.ts(lockedUntil))
                .param("id", userId)
                .update();
    }

    public void recordSuccessfulLogin(UUID userId) {
        jdbc.sql("""
                update users
                   set failed_login_count = 0,
                       locked_until = null,
                       last_login_at = now(),
                       updated_at = now()
                 where id = :id
                """)
                .param("id", userId)
                .update();
    }

    public void bumpTokenVersionAndSetPassword(UUID userId, String passwordHash) {
        jdbc.sql("""
                update users
                   set password_hash = :hash,
                       token_version = token_version + 1,
                       updated_at = now()
                 where id = :id
                """)
                .param("hash", passwordHash)
                .param("id", userId)
                .update();
    }

    public Optional<AuthUser> findById(UUID userId) {
        return jdbc.sql("""
                select id, tenant_id, full_name, password_hash, role_code, status,
                       token_version, failed_login_count, locked_until
                  from users
                 where id = :id
                """)
                .param("id", userId)
                .query((rs, i) -> new AuthUser(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("password_hash"),
                        rs.getString("role_code"),
                        rs.getString("status"),
                        rs.getInt("token_version"),
                        rs.getInt("failed_login_count"),
                        toInstant(rs.getTimestamp("locked_until"))))
                .optional();
    }

    public Optional<SessionSnapshot> session(UUID userId) {
        return jdbc.sql("""
                select token_version, user_status, tenant_status, tenant_id, role_code, full_name
                  from auth_session_state(:id)
                """)
                .param("id", userId)
                .query((rs, i) -> new SessionSnapshot(
                        rs.getInt("token_version"),
                        rs.getString("user_status"),
                        rs.getString("tenant_status"),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("role_code"),
                        rs.getString("full_name")))
                .optional();
    }

    public record SessionSnapshot(
            int tokenVersion,
            String userStatus,
            String tenantStatus,
            UUID tenantId,
            String roleCode,
            String fullName) {}

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
