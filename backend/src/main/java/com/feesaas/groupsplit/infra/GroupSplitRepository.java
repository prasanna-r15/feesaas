package com.feesaas.groupsplit.infra;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GroupSplitRepository {

    private final JdbcClient jdbc;

    public GroupSplitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID createGroup(UUID userId, String name, String type, String creatorName) {
        return jdbc.sql("select create_expense_group(:userId, :name, :type, :creator)")
                .param("userId", userId).param("name", name).param("type", type).param("creator", creatorName)
                .query(UUID.class).single();
    }

    public List<GroupRow> listGroups(UUID userId) {
        return jdbc.sql("""
                select g.id, g.name, g.group_type, g.created_at
                  from expense_groups g
                  join user_memberships m on m.group_id = g.id and m.kind = 'GROUP' and m.user_id = :userId
                 order by g.created_at desc
                """)
                .param("userId", userId)
                .query((rs, i) -> new GroupRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("group_type")))
                .list();
    }

    public Optional<GroupRow> getGroup(UUID groupId) {
        return jdbc.sql("select id, name, group_type from expense_groups where id = :id")
                .param("id", groupId)
                .query((rs, i) -> new GroupRow(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("group_type")))
                .optional();
    }

    public Optional<InvitePreview> invitePreview(String tokenHash) {
        return jdbc.sql("select * from public_group_invite_preview(:hash)")
                .param("hash", tokenHash)
                .query((rs, i) -> new InvitePreview(
                        rs.getObject("group_id", UUID.class),
                        rs.getString("group_name"),
                        rs.getString("group_type"),
                        rs.getLong("member_count"),
                        rs.getString("invited_by")))
                .optional();
    }

    public List<MemberRow> members(UUID groupId) {
        return jdbc.sql("""
                select id, user_id, display_name, status from expense_group_members
                 where group_id = :g order by created_at
                """)
                .param("g", groupId)
                .query((rs, i) -> new MemberRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("status")))
                .list();
    }

    public UUID addMember(UUID groupId, UUID userId, String name) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into expense_group_members (id, group_id, user_id, display_name, status)
                values (:id, :g, :userId, :name, 'ACTIVE')
                """)
                .param("id", id).param("g", groupId).param("userId", userId).param("name", name)
                .update();
        if (userId != null) {
            jdbc.sql("""
                    insert into user_memberships (id, user_id, kind, role_code, group_id)
                    values (:id, :userId, 'GROUP', 'MEMBER', :g)
                    on conflict do nothing
                    """)
                    .param("id", UuidCreator.getTimeOrderedEpoch()).param("userId", userId).param("g", groupId)
                    .update();
        }
        return id;
    }

    public UUID insertExpense(UUID groupId, String description, long amount, UUID paidBy, String method, LocalDate on, UUID createdBy) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into group_expenses (id, group_id, description, amount_minor, currency, paid_by, split_method, occurred_on, created_by)
                values (:id, :g, :desc, :amt, 'INR', :paid, :method, :on, :by)
                """)
                .param("id", id).param("g", groupId).param("desc", description).param("amt", amount)
                .param("paid", paidBy).param("method", method).param("on", on).param("by", createdBy)
                .update();
        return id;
    }

    public void insertShare(UUID expenseId, UUID memberId, long share, java.math.BigDecimal weight) {
        jdbc.sql("""
                insert into group_expense_shares (id, expense_id, member_id, share_minor, weight)
                values (:id, :e, :m, :share, :w)
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch()).param("e", expenseId)
                .param("m", memberId).param("share", share).param("w", weight)
                .update();
    }

    public List<ExpenseRow> expenses(UUID groupId) {
        return jdbc.sql("""
                select e.id, e.description, e.amount_minor, e.split_method, e.occurred_on, e.paid_by, e.created_by,
                       m.display_name as payer_name
                  from group_expenses e
                  join expense_group_members m on m.id = e.paid_by
                 where e.group_id = :g
                 order by e.created_at desc
                """)
                .param("g", groupId)
                .query(this::mapExpense)
                .list();
    }

    public Optional<ExpenseRow> findExpense(UUID groupId, UUID expenseId) {
        return jdbc.sql("""
                select e.id, e.description, e.amount_minor, e.split_method, e.occurred_on, e.paid_by, e.created_by,
                       m.display_name as payer_name
                  from group_expenses e
                  join expense_group_members m on m.id = e.paid_by
                 where e.group_id = :g and e.id = :id
                """)
                .param("g", groupId).param("id", expenseId)
                .query(this::mapExpense)
                .optional();
    }

    private ExpenseRow mapExpense(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ExpenseRow(
                rs.getObject("id", UUID.class),
                rs.getString("description"),
                rs.getLong("amount_minor"),
                rs.getString("split_method"),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getObject("paid_by", UUID.class),
                rs.getObject("created_by", UUID.class),
                rs.getString("payer_name"));
    }

    public List<ShareRow> shares(UUID expenseId) {
        return jdbc.sql("""
                select s.member_id, s.share_minor, s.settled_at, m.display_name
                  from group_expense_shares s
                  join expense_group_members m on m.id = s.member_id
                 where s.expense_id = :e
                 order by m.created_at
                """)
                .param("e", expenseId)
                .query((rs, i) -> new ShareRow(
                        rs.getObject("member_id", UUID.class),
                        rs.getLong("share_minor"),
                        rs.getString("display_name"),
                        rs.getTimestamp("settled_at") != null))
                .list();
    }

    public List<OpenShareRow> openShares(UUID groupId) {
        return jdbc.sql("""
                select s.member_id as debtor_id, e.paid_by as creditor_id, s.share_minor
                  from group_expense_shares s
                  join group_expenses e on e.id = s.expense_id
                 where e.group_id = :g
                   and s.settled_at is null
                   and s.member_id <> e.paid_by
                """)
                .param("g", groupId)
                .query((rs, i) -> new OpenShareRow(
                        rs.getObject("debtor_id", UUID.class),
                        rs.getObject("creditor_id", UUID.class),
                        rs.getLong("share_minor")))
                .list();
    }

    public void replaceShares(UUID expenseId, Map<UUID, Long> split) {
        jdbc.sql("delete from group_expense_shares where expense_id = :e").param("e", expenseId).update();
        split.forEach((memberId, share) -> insertShare(expenseId, memberId, share, null));
    }

    public void updateExpense(UUID expenseId, String description, long amount, UUID paidBy, String method) {
        jdbc.sql("""
                update group_expenses
                   set description = :desc, amount_minor = :amt, paid_by = :paid, split_method = :method
                 where id = :id
                """)
                .param("id", expenseId).param("desc", description).param("amt", amount)
                .param("paid", paidBy).param("method", method)
                .update();
    }

    public int markShareSettled(UUID expenseId, UUID memberId, UUID actorId) {
        return jdbc.sql("""
                update group_expense_shares
                   set settled_at = now(), settled_by = :actor
                 where expense_id = :e and member_id = :m and settled_at is null
                """)
                .param("e", expenseId).param("m", memberId).param("actor", actorId)
                .update();
    }

    public Map<UUID, Long> paidTotals(UUID groupId) {
        var rows = jdbc.sql("""
                select paid_by, sum(amount_minor) as paid from group_expenses where group_id = :g group by paid_by
                """).param("g", groupId)
                .query((rs, i) -> Map.entry(rs.getObject("paid_by", UUID.class), rs.getLong("paid")))
                .list();
        return rows.stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
    }

    public Map<UUID, Long> shareTotals(UUID groupId) {
        var rows = jdbc.sql("""
                select s.member_id, sum(s.share_minor) as share
                  from group_expense_shares s
                  join group_expenses e on e.id = s.expense_id
                 where e.group_id = :g
                 group by s.member_id
                """).param("g", groupId)
                .query((rs, i) -> Map.entry(rs.getObject("member_id", UUID.class), rs.getLong("share")))
                .list();
        return rows.stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
    }

    public Map<UUID, Long> settlementNets(UUID groupId) {
        var rows = jdbc.sql("""
                select payer_id as member_id, -sum(amount_minor) as net from group_settlements where group_id = :g group by payer_id
                union all
                select receiver_id, sum(amount_minor) from group_settlements where group_id = :g group by receiver_id
                """).param("g", groupId)
                .query((rs, i) -> Map.entry(rs.getObject("member_id", UUID.class), rs.getLong("net")))
                .list();
        return rows.stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
    }

    public UUID insertSettlement(UUID groupId, UUID payer, UUID receiver, long amount, String method, String notes, LocalDate on, UUID createdBy) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into group_settlements (id, group_id, payer_id, receiver_id, amount_minor, currency, method, notes, occurred_on, created_by)
                values (:id, :g, :payer, :recv, :amt, 'INR', :method, :notes, :on, :by)
                """)
                .param("id", id).param("g", groupId).param("payer", payer).param("recv", receiver)
                .param("amt", amount).param("method", method).param("notes", notes).param("on", on).param("by", createdBy)
                .update();
        return id;
    }

    public void activity(UUID groupId, UUID actor, String verb, String summary) {
        jdbc.sql("""
                insert into group_activity (id, group_id, actor_id, verb, summary)
                values (:id, :g, :actor, :verb, :summary)
                """)
                .param("id", UuidCreator.getTimeOrderedEpoch()).param("g", groupId).param("actor", actor)
                .param("verb", verb).param("summary", summary)
                .update();
    }

    public List<ActivityRow> activity(UUID groupId) {
        return jdbc.sql("""
                select summary, created_at from group_activity where group_id = :g order by created_at desc limit 50
                """)
                .param("g", groupId)
                .query((rs, i) -> new ActivityRow(rs.getString("summary"), rs.getTimestamp("created_at").toInstant().toString()))
                .list();
    }

    public UUID insertInvite(UUID groupId, String hash, String email, String phone, UUID createdBy, java.time.Instant expires) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.sql("""
                insert into group_invitations (id, group_id, token_hash, email, phone, created_by, expires_at)
                values (:id, :g, :hash, :email, :phone, :by, :exp)
                """)
                .param("id", id).param("g", groupId).param("hash", hash).param("email", email)
                .param("phone", phone).param("by", createdBy).param("exp", java.sql.Timestamp.from(expires))
                .update();
        return id;
    }

    public UUID acceptInvite(String hash, UUID userId, String name) {
        return jdbc.sql("select auth_accept_group_invite(:hash, :userId, :name)")
                .param("hash", hash).param("userId", userId).param("name", name)
                .query(UUID.class).single();
    }

    public long totalExpenses(UUID groupId) {
        return jdbc.sql("select coalesce(sum(amount_minor),0) from group_expenses where group_id = :g")
                .param("g", groupId).query(Long.class).optional().orElse(0L);
    }

    public record GroupRow(UUID id, String name, String type) {}
    public record MemberRow(UUID id, UUID userId, String displayName, String status) {}
    public record ExpenseRow(
            UUID id,
            String description,
            long amountMinor,
            String splitMethod,
            LocalDate occurredOn,
            UUID paidBy,
            UUID createdBy,
            String payerName
    ) {}
    public record ShareRow(UUID memberId, long shareMinor, String displayName, boolean settled) {}
    public record OpenShareRow(UUID debtorId, UUID creditorId, long amountMinor) {}
    public record InvitePreview(UUID groupId, String groupName, String groupType, long memberCount, String invitedBy) {}
    public record ActivityRow(String summary, String createdAt) {}
}
