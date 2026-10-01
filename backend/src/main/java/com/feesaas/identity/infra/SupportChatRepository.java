package com.feesaas.identity.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SupportChatRepository {

    private final JdbcClient jdbc;

    public SupportChatRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID ensureThread(UUID userId) {
        Optional<UUID> existing = jdbc.sql("select id from support_threads where user_id = :uid")
                .param("uid", userId)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UuidCreator.getTimeOrderedEpoch();
        try {
            jdbc.sql("insert into support_threads (id, user_id) values (:id, :uid)")
                    .param("id", id)
                    .param("uid", userId)
                    .update();
            return id;
        } catch (DuplicateKeyException e) {
            return jdbc.sql("select id from support_threads where user_id = :uid")
                    .param("uid", userId)
                    .query(UUID.class)
                    .single();
        }
    }

    public Optional<UUID> threadOwner(UUID threadId) {
        return jdbc.sql("select user_id from support_threads where id = :id")
                .param("id", threadId)
                .query(UUID.class)
                .optional();
    }

    public UUID insertMessage(UUID threadId, UUID authorUserId, boolean fromPlatform, String body) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into support_messages (id, thread_id, author_user_id, from_platform, body)
                values (:id, :tid, :aid, :plat, :body)
                """)
                .param("id", id)
                .param("tid", threadId)
                .param("aid", authorUserId)
                .param("plat", fromPlatform)
                .param("body", body)
                .update();
        return id;
    }

    public List<MessageRow> messages(UUID threadId) {
        return jdbc.sql("""
                select m.id, m.thread_id, m.author_user_id, m.from_platform, m.body, m.created_at, u.full_name
                  from support_messages m
                  left join users u on u.id = m.author_user_id
                 where m.thread_id = :tid
                 order by m.created_at
                """)
                .param("tid", threadId)
                .query((rs, i) -> new MessageRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("thread_id", UUID.class),
                        rs.getObject("author_user_id", UUID.class),
                        rs.getBoolean("from_platform"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("full_name")))
                .list();
    }

    public Optional<MessageRow> message(UUID id) {
        return jdbc.sql("""
                select m.id, m.thread_id, m.author_user_id, m.from_platform, m.body, m.created_at, u.full_name
                  from support_messages m
                  left join users u on u.id = m.author_user_id
                 where m.id = :id
                """)
                .param("id", id)
                .query((rs, i) -> new MessageRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("thread_id", UUID.class),
                        rs.getObject("author_user_id", UUID.class),
                        rs.getBoolean("from_platform"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("full_name")))
                .optional();
    }

    public List<ThreadRow> listThreads() {
        return jdbc.sql("""
                select t.id, t.user_id, u.full_name, u.email,
                       (select body from support_messages m where m.thread_id = t.id order by created_at desc limit 1) as last_body,
                       coalesce(
                         (select created_at from support_messages m where m.thread_id = t.id order by created_at desc limit 1),
                         t.created_at
                       ) as last_at,
                       (select count(*) from support_messages m
                         where m.thread_id = t.id
                           and m.from_platform = false
                           and m.created_at > coalesce(t.platform_last_read_at, timestamptz '1970-01-01+00')) as unread
                  from support_threads t
                  join users u on u.id = t.user_id
                 order by last_at desc
                 limit 200
                """)
                .query((rs, i) -> new ThreadRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("last_body"),
                        rs.getTimestamp("last_at") == null ? null : rs.getTimestamp("last_at").toInstant(),
                        rs.getLong("unread")))
                .list();
    }

    public long unreadForUser(UUID userId) {
        return jdbc.sql("""
                select count(*) from support_messages m
                  join support_threads t on t.id = m.thread_id
                 where t.user_id = :uid
                   and m.from_platform = true
                   and m.created_at > coalesce(t.user_last_read_at, timestamptz '1970-01-01+00')
                """)
                .param("uid", userId)
                .query(Long.class)
                .single();
    }

    public long unreadForPlatform() {
        return jdbc.sql("""
                select coalesce(sum(unread), 0) from (
                  select (select count(*) from support_messages m
                           where m.thread_id = t.id
                             and m.from_platform = false
                             and m.created_at > coalesce(t.platform_last_read_at, timestamptz '1970-01-01+00')) as unread
                    from support_threads t
                ) x
                """)
                .query(Long.class)
                .single();
    }

    public void markUserRead(UUID userId) {
        jdbc.sql("update support_threads set user_last_read_at = now() where user_id = :uid")
                .param("uid", userId)
                .update();
    }

    public void markPlatformRead(UUID threadId) {
        jdbc.sql("update support_threads set platform_last_read_at = now() where id = :id")
                .param("id", threadId)
                .update();
    }

    public record MessageRow(
            UUID id,
            UUID threadId,
            UUID authorUserId,
            boolean fromPlatform,
            String body,
            Instant createdAt,
            String authorName
    ) {}

    public record ThreadRow(
            UUID id,
            UUID userId,
            String fullName,
            String email,
            String lastBody,
            Instant lastAt,
            long unreadCount
    ) {}
}
