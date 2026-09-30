package com.feesaas.groupsplit.application;

import com.feesaas.auth.infra.AuthUserRepository;
import com.feesaas.groupsplit.domain.PairwiseTally;
import com.feesaas.groupsplit.domain.SplitCalculator;
import com.feesaas.groupsplit.domain.SplitCalculator.ShareInput;
import com.feesaas.groupsplit.infra.GroupSplitRepository;
import com.feesaas.groupsplit.infra.GroupSplitRepository.ExpenseRow;
import com.feesaas.groupsplit.infra.GroupSplitRepository.InvitePreview;
import com.feesaas.groupsplit.infra.GroupSplitRepository.MemberRow;
import com.feesaas.groupsplit.infra.GroupSplitRepository.ShareRow;
import com.feesaas.shared.error.ApiException;
import com.feesaas.shared.error.ErrorCode;
import com.feesaas.shared.security.TokenHasher;
import com.feesaas.shared.tenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSplitService {

    private final GroupSplitRepository groups;
    private final AuthUserRepository users;

    public GroupSplitService(GroupSplitRepository groups, AuthUserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public GroupSplitRepository.GroupRow create(String name, String type) {
        UUID userId = TenantContext.requireUserId();
        String creator = users.findById(userId).map(u -> u.fullName()).orElse("You");
        UUID id = groups.createGroup(userId, name.trim(), type == null ? "CUSTOM" : type, creator);
        return groups.getGroup(id).orElseThrow();
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public List<GroupSplitRepository.GroupRow> list() {
        return groups.listGroups(TenantContext.requireUserId());
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public GroupSplitRepository.GroupRow get(UUID groupId) {
        requireMember(groupId);
        return groups.getGroup(groupId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Group not found."));
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public MemberRow addMember(UUID groupId, String name) {
        requireMember(groupId);
        UUID id = groups.addMember(groupId, null, name.trim());
        groups.activity(groupId, TenantContext.requireUserId(), "MEMBER", name.trim() + " joined");
        return groups.members(groupId).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public ExpenseView addExpense(UUID groupId, String description, long amountMinor, UUID paidBy,
            String method, List<ShareInput> shares, LocalDate occurredOn) {
        requireMember(groupId);
        if (amountMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
        UUID payer = requireMyMember(groupId).id();
        Map<UUID, Long> split = SplitCalculator.split(amountMinor, method, shares);
        UUID expenseId = groups.insertExpense(groupId, description.trim(), amountMinor, payer,
                method == null ? "EQUAL" : method.toUpperCase(),
                occurredOn == null ? LocalDate.now() : occurredOn, TenantContext.requireUserId());
        split.forEach((memberId, share) -> groups.insertShare(expenseId, memberId, share, null));
        groups.activity(groupId, TenantContext.requireUserId(), "EXPENSE",
                description.trim() + " ₹" + (amountMinor / 100.0));
        return expenseView(groupId, groups.findExpense(groupId, expenseId).orElseThrow());
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public ExpenseView updateExpense(UUID groupId, UUID expenseId, String description, long amountMinor, UUID paidBy,
            String method, List<ShareInput> shares) {
        ExpenseRow expense = requireExpenseCreator(groupId, expenseId);
        if (amountMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero.");
        }
        Map<UUID, Long> split = SplitCalculator.split(amountMinor, method, shares);
        groups.updateExpense(expense.id(), description.trim(), amountMinor, expense.paidBy(),
                method == null ? expense.splitMethod() : method.toUpperCase());
        groups.replaceShares(expense.id(), split);
        groups.activity(groupId, TenantContext.requireUserId(), "EXPENSE", "Updated " + description.trim());
        return expenseView(groupId, groups.findExpense(groupId, expenseId).orElseThrow());
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public void markSharePaid(UUID groupId, UUID expenseId, UUID memberId) {
        ExpenseRow expense = requireExpenseCreator(groupId, expenseId);
        if (memberId.equals(expense.paidBy())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The person who paid this bill is already covered.");
        }
        int updated = groups.markShareSettled(expenseId, memberId, TenantContext.requireUserId());
        if (updated == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That share is already marked paid.");
        }
        String who = groups.members(groupId).stream()
                .filter(m -> m.id().equals(memberId))
                .map(MemberRow::displayName)
                .findFirst()
                .orElse("Member");
        groups.activity(groupId, TenantContext.requireUserId(), "SETTLE", who + " settled " + expense.description());
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public BalanceView balances(UUID groupId) {
        requireMember(groupId);
        List<MemberRow> members = groups.members(groupId).stream().filter(m -> "ACTIVE".equals(m.status())).toList();
        Map<UUID, Long> paid = groups.paidTotals(groupId);
        List<PairwiseTally.OpenShare> opens = groups.openShares(groupId).stream()
                .map(s -> new PairwiseTally.OpenShare(s.debtorId(), s.creditorId(), s.amountMinor()))
                .toList();
        List<PairwiseTally.Transfer> transfers = PairwiseTally.net(opens);
        UUID me = TenantContext.requireUserId();
        MemberRow mine = members.stream().filter(m -> me.equals(m.userId())).findFirst().orElse(null);
        List<Line> lines = new ArrayList<>();
        long myNet = 0;
        for (PairwiseTally.Transfer t : transfers) {
            if (mine != null) {
                if (t.fromMemberId().equals(mine.id())) {
                    myNet -= t.amountMinor();
                } else if (t.toMemberId().equals(mine.id())) {
                    myNet += t.amountMinor();
                }
            }
            lines.add(new Line(
                    t.fromMemberId(),
                    t.toMemberId(),
                    name(members, t.fromMemberId()),
                    name(members, t.toMemberId()),
                    t.amountMinor(),
                    mine != null && t.fromMemberId().equals(mine.id()),
                    mine != null && t.toMemberId().equals(mine.id())));
        }
        long total = groups.totalExpenses(groupId);
        long myPaid = mine == null ? 0 : paid.getOrDefault(mine.id(), 0L);
        Map<UUID, Long> paidByMember = new HashMap<>(paid);
        List<MemberPaid> paidRows = members.stream()
                .map(m -> new MemberPaid(m.id(), m.displayName(), paidByMember.getOrDefault(m.id(), 0L)))
                .toList();
        String groupName = groups.getGroup(groupId).map(GroupSplitRepository.GroupRow::name).orElse("Group");
        return new BalanceView(groupName, total, myPaid, myNet, lines, members, paidRows);
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public InviteCreated invite(UUID groupId, String email, String phone) {
        requireMember(groupId);
        String raw = TokenHasher.randomUrlToken();
        groups.insertInvite(groupId, TokenHasher.sha256(raw), email, phone, TenantContext.requireUserId(),
                Instant.now().plus(30, ChronoUnit.DAYS));
        var group = groups.getGroup(groupId).orElseThrow();
        long members = groups.members(groupId).stream().filter(m -> "ACTIVE".equals(m.status())).count();
        return new InviteCreated(raw, "/group/invite/" + raw, group.name(), members);
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional
    public UUID acceptInvite(String rawToken) {
        UUID userId = TenantContext.requireUserId();
        String name = users.findById(userId).map(u -> u.fullName()).orElse("Member");
        try {
            return groups.acceptInvite(TokenHasher.sha256(rawToken), userId, name);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This invite is invalid or has already expired.");
        }
    }

    @Transactional(readOnly = true)
    public InvitePreview previewInvite(String rawToken) {
        return groups.invitePreview(TokenHasher.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "This invite is invalid or has expired."));
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public List<ExpenseView> expenses(UUID groupId) {
        requireMember(groupId);
        return groups.expenses(groupId).stream().map(e -> expenseView(groupId, e)).toList();
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public List<GroupSplitRepository.ActivityRow> activity(UUID groupId) {
        requireMember(groupId);
        return groups.activity(groupId);
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public List<MemberRow> members(UUID groupId) {
        requireMember(groupId);
        return groups.members(groupId);
    }

    @PreAuthorize("hasPermission(null, 'groups.manage')")
    @Transactional(readOnly = true)
    public List<ShareRow> shares(UUID expenseId, UUID groupId) {
        requireMember(groupId);
        return groups.shares(expenseId);
    }

    private ExpenseView expenseView(UUID groupId, ExpenseRow expense) {
        UUID me = TenantContext.requireUserId();
        boolean canEdit = me.equals(expense.createdBy());
        List<ShareView> shares = groups.shares(expense.id()).stream()
                .map(s -> new ShareView(
                        s.memberId(),
                        s.displayName(),
                        s.shareMinor(),
                        s.settled(),
                        s.memberId().equals(expense.paidBy())))
                .toList();
        return new ExpenseView(
                expense.id(),
                expense.description(),
                expense.amountMinor(),
                expense.splitMethod(),
                expense.paidBy(),
                expense.payerName(),
                expense.createdBy(),
                canEdit,
                shares);
    }

    private ExpenseRow requireExpenseCreator(UUID groupId, UUID expenseId) {
        requireMember(groupId);
        ExpenseRow expense = groups.findExpense(groupId, expenseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Split not found."));
        if (!TenantContext.requireUserId().equals(expense.createdBy())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the person who created this split can change it.");
        }
        return expense;
    }

    private MemberRow requireMyMember(UUID groupId) {
        UUID userId = TenantContext.requireUserId();
        return groups.members(groupId).stream()
                .filter(m -> userId.equals(m.userId()) && "ACTIVE".equals(m.status()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "You must be in this group to add a split."));
    }

    private void requireMember(UUID groupId) {
        UUID userId = TenantContext.requireUserId();
        boolean ok = groups.members(groupId).stream().anyMatch(m -> userId.equals(m.userId()) && "ACTIVE".equals(m.status()));
        if (!ok) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not in this group.");
        }
    }

    private static String name(List<MemberRow> members, UUID id) {
        return members.stream().filter(m -> m.id().equals(id)).map(MemberRow::displayName).findFirst().orElse("Someone");
    }

    public record Line(
            UUID fromMemberId,
            UUID toMemberId,
            String fromName,
            String toName,
            long amountMinor,
            boolean youPay,
            boolean youReceive
    ) {}

    public record MemberPaid(UUID memberId, String name, long paidMinor) {}

    public record BalanceView(
            String groupName,
            long totalMinor,
            long myPaidMinor,
            long myNetMinor,
            List<Line> suggested,
            List<MemberRow> members,
            List<MemberPaid> paid
    ) {}

    public record ShareView(UUID memberId, String displayName, long shareMinor, boolean settled, boolean isPayer) {}

    public record ExpenseView(
            UUID id,
            String description,
            long amountMinor,
            String splitMethod,
            UUID paidBy,
            String payerName,
            UUID createdBy,
            boolean canEdit,
            List<ShareView> shares
    ) {}

    public record InviteCreated(String token, String path, String groupName, long memberCount) {}
}
