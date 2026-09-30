package com.feesaas.groupsplit.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/** Presentation-only settlement suggestions. Does not mutate the ledger. */
public final class DebtSimplifier {

    private DebtSimplifier() {}

    public static List<Transfer> simplify(Map<UUID, Long> netByMember) {
        PriorityQueue<Entry> debtors = new PriorityQueue<>(Comparator.comparingLong(Entry::amount));
        PriorityQueue<Entry> creditors = new PriorityQueue<>(Comparator.comparingLong(Entry::amount).reversed());
        netByMember.forEach((id, net) -> {
            if (net < 0) {
                debtors.add(new Entry(id, -net));
            } else if (net > 0) {
                creditors.add(new Entry(id, net));
            }
        });
        List<Transfer> out = new ArrayList<>();
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            Entry d = debtors.poll();
            Entry c = creditors.poll();
            long pay = Math.min(d.amount, c.amount);
            out.add(new Transfer(d.id, c.id, pay));
            if (d.amount > pay) {
                debtors.add(new Entry(d.id, d.amount - pay));
            }
            if (c.amount > pay) {
                creditors.add(new Entry(c.id, c.amount - pay));
            }
        }
        return out;
    }

    private record Entry(UUID id, long amount) {}

    public record Transfer(UUID fromMemberId, UUID toMemberId, long amountMinor) {}
}
