package com.feesaas.identity.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IndividualDirectoryRepository {

    private final JdbcClient jdbc;

    public IndividualDirectoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<IndividualRow> list(String query) {
        String q = query == null ? "" : query.trim();
        return jdbc.sql("""
                select u.id,
                       u.full_name,
                       u.email,
                       u.phone,
                       u.status,
                       u.last_login_at,
                       u.created_at,
                       (select count(*) from expense_group_members g
                         where g.user_id = u.id and g.status = 'ACTIVE') as group_member_count,
                       (select count(*) from user_memberships m
                         where m.user_id = u.id and m.kind = 'GROUP') as groups_joined,
                       (select count(*) from personal_expenses e
                         join personal_workspaces w on w.id = e.workspace_id
                        where w.owner_user_id = u.id) as expense_count
                  from users u
                 where u.role_code = 'INDIVIDUAL'
                   and (
                     :q = ''
                     or lower(u.full_name) like lower(:like)
                     or lower(coalesce(u.email, '')) like lower(:like)
                     or coalesce(u.phone, '') like :like
                   )
                 order by u.last_login_at desc nulls last, u.created_at desc
                """)
                .param("q", q)
                .param("like", "%" + q + "%")
                .query((rs, i) -> new IndividualRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("status"),
                        rs.getTimestamp("last_login_at") == null
                                ? null
                                : rs.getTimestamp("last_login_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("group_member_count"),
                        rs.getLong("groups_joined"),
                        rs.getLong("expense_count")))
                .list();
    }

    public record IndividualRow(
            UUID id,
            String fullName,
            String email,
            String phone,
            String status,
            Instant lastLoginAt,
            Instant createdAt,
            long groupMemberCount,
            long groupsJoined,
            long expenseCount
    ) {}
}
