package com.feesaas.auth.infra;

import com.feesaas.shared.persistence.JdbcTimes;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbc;

    public RefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID userId,
            UUID tenantId,
            UUID familyId,
            String tokenHash,
            String deviceId,
            Instant expiresAt,
            String contextKind,
            UUID workspaceId,
            UUID groupId) {
        jdbc.sql("""
                insert into refresh_tokens (
                    id, user_id, tenant_id, family_id, token_hash, device_id, expires_at,
                    context_kind, workspace_id, group_id)
                values (:id, :userId, :tenantId, :familyId, :hash, :deviceId, :expiresAt, :ctx, :ws, :grp)
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch())
                .param("userId", userId)
                .param("tenantId", tenantId)
                .param("familyId", familyId)
                .param("hash", tokenHash)
                .param("deviceId", deviceId)
                .param("expiresAt", JdbcTimes.ts(expiresAt))
                .param("ctx", contextKind)
                .param("ws", workspaceId)
                .param("grp", groupId)
                .update();
    }

    public Optional<StoredRefreshToken> findByHash(String tokenHash) {
        return jdbc.sql("""
                select id, user_id, tenant_id, family_id, device_id, expires_at, used_at, revoked_at,
                       context_kind, workspace_id, group_id
                  from refresh_tokens
                 where token_hash = :hash
                """)
                .param("hash", tokenHash)
                .query((rs, i) -> new StoredRefreshToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("family_id", UUID.class),
                        rs.getString("device_id"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getString("context_kind"),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("group_id", UUID.class)))
                .optional();
    }

    public void markUsed(UUID id, UUID replacedBy) {
        jdbc.sql("update refresh_tokens set used_at = now(), replaced_by = :next where id = :id")
                .param("next", replacedBy)
                .param("id", id)
                .update();
    }

    public UUID insertReturningId(
            UUID userId,
            UUID tenantId,
            UUID familyId,
            String tokenHash,
            String deviceId,
            Instant expiresAt,
            String contextKind,
            UUID workspaceId,
            UUID groupId) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into refresh_tokens (
                    id, user_id, tenant_id, family_id, token_hash, device_id, expires_at,
                    context_kind, workspace_id, group_id)
                values (:id, :userId, :tenantId, :familyId, :hash, :deviceId, :expiresAt, :ctx, :ws, :grp)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("tenantId", tenantId)
                .param("familyId", familyId)
                .param("hash", tokenHash)
                .param("deviceId", deviceId)
                .param("expiresAt", JdbcTimes.ts(expiresAt))
                .param("ctx", contextKind)
                .param("ws", workspaceId)
                .param("grp", groupId)
                .update();
        return id;
    }

    public void revokeFamily(UUID familyId) {
        jdbc.sql("update refresh_tokens set revoked_at = now() where family_id = :family and revoked_at is null")
                .param("family", familyId)
                .update();
    }

    public void revokeAllForUser(UUID userId) {
        jdbc.sql("update refresh_tokens set revoked_at = now() where user_id = :userId and revoked_at is null")
                .param("userId", userId)
                .update();
    }

    public record StoredRefreshToken(
            UUID id,
            UUID userId,
            UUID tenantId,
            UUID familyId,
            String deviceId,
            Instant expiresAt,
            Instant usedAt,
            Instant revokedAt,
            String contextKind,
            UUID workspaceId,
            UUID groupId
    ) {}
}
