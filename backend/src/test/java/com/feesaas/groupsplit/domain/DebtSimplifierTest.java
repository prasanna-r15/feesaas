package com.feesaas.groupsplit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebtSimplifierTest {

    @Test
    void collapsesCycleToNetTransfers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<DebtSimplifier.Transfer> out = DebtSimplifier.simplify(Map.of(
                a, -500L,
                b, 0L,
                c, 500L));
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().fromMemberId()).isEqualTo(a);
        assertThat(out.getFirst().toMemberId()).isEqualTo(c);
        assertThat(out.getFirst().amountMinor()).isEqualTo(500L);
    }
}
