package com.feesaas.config.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformConfigRepository {

    private final JdbcClient jdbc;

    public PlatformConfigRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConfigRow> list(String query, String paramKey, String paramSubKey) {
        String q = query == null ? "" : query.trim();
        String key = paramKey == null ? "" : paramKey.trim();
        String sub = paramSubKey == null ? "" : paramSubKey.trim();
        return jdbc.sql("""
                select id, param_key, param_sub_key, param_value, description, locked, created_at, updated_at
                  from platform_config
                 where (:q = '' or param_key ilike :like or param_sub_key ilike :like or coalesce(description, '') ilike :like)
                   and (:key = '' or param_key = :key)
                   and (:sub = '' or param_sub_key = :sub)
                 order by param_key, param_sub_key
                """)
                .param("q", q)
                .param("like", "%" + q + "%")
                .param("key", key)
                .param("sub", sub)
                .query(PlatformConfigRepository::map)
                .list();
    }

    public Optional<ConfigRow> find(UUID id) {
        return jdbc.sql("""
                select id, param_key, param_sub_key, param_value, description, locked, created_at, updated_at
                  from platform_config
                 where id = :id
                """)
                .param("id", id)
                .query(PlatformConfigRepository::map)
                .optional();
    }

    public Optional<String> resolve(String paramKey, String paramSubKey) {
        String sub = paramSubKey == null || paramSubKey.isBlank() ? "DEFAULT" : paramSubKey.trim();
        Optional<String> exact = jdbc.sql("""
                select param_value from platform_config
                 where param_key = :k and upper(param_sub_key) = upper(:s)
                 limit 1
                """)
                .param("k", paramKey)
                .param("s", sub)
                .query(String.class)
                .optional();
        if (exact.isPresent() || "DEFAULT".equalsIgnoreCase(sub)) {
            return exact;
        }
        return jdbc.sql("""
                select param_value from platform_config
                 where param_key = :k and upper(param_sub_key) = 'DEFAULT'
                 limit 1
                """)
                .param("k", paramKey)
                .query(String.class)
                .optional();
    }

    public UUID insert(String paramKey, String paramSubKey, String paramValue, String description, boolean locked) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into platform_config (id, param_key, param_sub_key, param_value, description, locked)
                values (:id, :k, :s, :v, :d, :locked)
                """)
                .param("id", id)
                .param("k", paramKey)
                .param("s", paramSubKey)
                .param("v", paramValue)
                .param("d", description)
                .param("locked", locked)
                .update();
        return id;
    }

    public int update(UUID id, String paramKey, String paramSubKey, String paramValue, String description) {
        return jdbc.sql("""
                update platform_config
                   set param_key = :k, param_sub_key = :s, param_value = :v, description = :d, updated_at = now()
                 where id = :id
                """)
                .param("id", id)
                .param("k", paramKey)
                .param("s", paramSubKey)
                .param("v", paramValue)
                .param("d", description)
                .update();
    }

    public int updateValue(UUID id, String paramValue, String description) {
        return jdbc.sql("""
                update platform_config
                   set param_value = :v, description = :d, updated_at = now()
                 where id = :id
                """)
                .param("id", id)
                .param("v", paramValue)
                .param("d", description)
                .update();
    }

    public int delete(UUID id) {
        return jdbc.sql("delete from platform_config where id = :id and locked = false")
                .param("id", id)
                .update();
    }

    private static ConfigRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ConfigRow(
                rs.getObject("id", UUID.class),
                rs.getString("param_key"),
                rs.getString("param_sub_key"),
                rs.getString("param_value"),
                rs.getString("description"),
                rs.getBoolean("locked"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    public record ConfigRow(
            UUID id,
            String paramKey,
            String paramSubKey,
            String paramValue,
            String description,
            boolean locked,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
