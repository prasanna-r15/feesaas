package com.feesaas.groupsplit.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pairwise net: A owes B minus B owes A. No third-party chaining. */
public final class PairwiseTally {

    private PairwiseTally() {}

    public record OpenShare(UUID debtorId, UUID creditorId, long amountMinor) {}

    public record Transfer(UUID fromMemberId, UUID toMemberId, long amountMinor) {}

    public static List<Transfer> net(List<OpenShare> opens) {
        Map<String, Long> acc = new HashMap<>();
        Map<String, UUID[]> pair = new HashMap<>();
        for (OpenShare share : opens) {
            if (share.debtorId() == null || share.creditorId() == null || share.debtorId().equals(share.creditorId())) {
                continue;
            }
            if (share.amountMinor() <= 0) {
                continue;
            }
            UUID a = share.debtorId().compareTo(share.creditorId()) < 0 ? share.debtorId() : share.creditorId();
            UUID b = a.equals(share.debtorId()) ? share.creditorId() : share.debtorId();
            String key = a + "|" + b;
            pair.putIfAbsent(key, new UUID[] {a, b});
            long signed = share.debtorId().equals(a) ? share.amountMinor() : -share.amountMinor();
            acc.merge(key, signed, Long::sum);
        }
        List<Transfer> out = new ArrayList<>();
        acc.forEach((key, value) -> {
            if (value == 0) {
                return;
            }
            UUID[] ids = pair.get(key);
            if (value > 0) {
                out.add(new Transfer(ids[0], ids[1], value));
            } else {
                out.add(new Transfer(ids[1], ids[0], -value));
            }
        });
        return out;
    }
}
