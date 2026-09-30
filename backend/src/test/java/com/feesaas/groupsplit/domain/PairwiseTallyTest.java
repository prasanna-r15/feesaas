package com.feesaas.groupsplit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PairwiseTallyTest {

    @Test
    void netsTwoWayDebtsToTheDifference() {
        UUID prasanna = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sakthi = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<PairwiseTally.Transfer> out = PairwiseTally.net(List.of(
                new PairwiseTally.OpenShare(sakthi, prasanna, 60_000),
                new PairwiseTally.OpenShare(prasanna, sakthi, 50_000)));
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().fromMemberId()).isEqualTo(sakthi);
        assertThat(out.getFirst().toMemberId()).isEqualTo(prasanna);
        assertThat(out.getFirst().amountMinor()).isEqualTo(10_000);
    }

    @Test
    void dropsEvenPairs() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(PairwiseTally.net(List.of(
                new PairwiseTally.OpenShare(a, b, 100),
                new PairwiseTally.OpenShare(b, a, 100)))).isEmpty();
    }
}
