package com.feesaas.groupsplit.api;

import com.feesaas.groupsplit.application.GroupSplitService;
import com.feesaas.groupsplit.domain.SplitCalculator.ShareInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GroupSplitController {

    private final GroupSplitService groups;

    public GroupSplitController(GroupSplitService groups) {
        this.groups = groups;
    }

    @PostMapping("/groups")
    public Object create(@Valid @RequestBody CreateGroupRequest request) {
        return groups.create(request.name(), request.type());
    }

    @GetMapping("/groups")
    public Object list() {
        return groups.list();
    }

    @GetMapping("/groups/{groupId}")
    public Object get(@PathVariable UUID groupId) {
        return groups.get(groupId);
    }

    @GetMapping("/groups/{groupId}/members")
    public Object members(@PathVariable UUID groupId) {
        return groups.members(groupId);
    }

    @PostMapping("/groups/{groupId}/members")
    public Object addMember(@PathVariable UUID groupId, @Valid @RequestBody AddMemberRequest request) {
        return groups.addMember(groupId, request.displayName());
    }

    @PostMapping("/groups/{groupId}/expenses")
    public Object addExpense(@PathVariable UUID groupId, @Valid @RequestBody AddExpenseRequest request) {
        return groups.addExpense(groupId, request.description(), request.amountMinor(), request.paidBy(),
                request.splitMethod(), shareInputs(request.shares()), request.occurredOn());
    }

    @PutMapping("/groups/{groupId}/expenses/{expenseId}")
    public Object updateExpense(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody AddExpenseRequest request) {
        return groups.updateExpense(groupId, expenseId, request.description(), request.amountMinor(), request.paidBy(),
                request.splitMethod(), shareInputs(request.shares()));
    }

    @PostMapping("/groups/{groupId}/expenses/{expenseId}/shares/{memberId}/settle")
    public void markSharePaid(
            @PathVariable UUID groupId,
            @PathVariable UUID expenseId,
            @PathVariable UUID memberId) {
        groups.markSharePaid(groupId, expenseId, memberId);
    }

    @GetMapping("/groups/{groupId}/expenses")
    public Object expenses(@PathVariable UUID groupId) {
        return groups.expenses(groupId);
    }

    @GetMapping("/groups/{groupId}/expenses/{expenseId}")
    public Object expenseDetail(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
        return Map.of("shares", groups.shares(expenseId, groupId));
    }

    @GetMapping("/groups/{groupId}/balances")
    public Object balances(@PathVariable UUID groupId) {
        var b = groups.balances(groupId);
        return Map.of(
                "groupName", b.groupName(),
                "totalMinor", b.totalMinor(),
                "myPaidMinor", b.myPaidMinor(),
                "myNetMinor", b.myNetMinor(),
                "suggested", b.suggested(),
                "members", b.members(),
                "paid", b.paid());
    }

    @GetMapping("/groups/{groupId}/activity")
    public Object activity(@PathVariable UUID groupId) {
        return groups.activity(groupId);
    }

    @PostMapping("/groups/{groupId}/invitations")
    public GroupSplitService.InviteCreated invite(
            @PathVariable UUID groupId,
            @RequestBody(required = false) InviteRequest request) {
        return groups.invite(groupId, request == null ? null : request.email(), request == null ? null : request.phone());
    }

    @PostMapping("/group-invitations/{token}")
    public Map<String, UUID> accept(@PathVariable String token) {
        return Map.of("groupId", groups.acceptInvite(token));
    }

    private static List<ShareInput> shareInputs(List<SharePart> shares) {
        return shares.stream()
                .map(s -> new ShareInput(s.memberId(), s.amountMinor(), s.percentBps(), s.shares()))
                .toList();
    }

    public record CreateGroupRequest(@NotBlank String name, String type) {}

    public record AddMemberRequest(@NotBlank String displayName, UUID userId) {}

    public record AddExpenseRequest(
            @NotBlank String description,
            @NotNull @Positive Long amountMinor,
            @NotNull UUID paidBy,
            String splitMethod,
            @NotNull List<SharePart> shares,
            LocalDate occurredOn
    ) {}

    public record SharePart(UUID memberId, Long amountMinor, Integer percentBps, Integer shares) {}

    public record InviteRequest(String email, String phone) {}
}
