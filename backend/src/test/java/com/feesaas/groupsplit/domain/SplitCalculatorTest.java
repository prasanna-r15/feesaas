package com.feesaas.groupsplit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feesaas.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

    @Test
    void equalSplitsRemainderToFirstMembers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, Long> out = SplitCalculator.split(100, "EQUAL", List.of(
                new SplitCalculator.ShareInput(a, null, null, null),
                new SplitCalculator.ShareInput(b, null, null, null),
                new SplitCalculator.ShareInput(c, null, null, null)));
        assertThat(out.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(100);
        assertThat(out.get(a)).isEqualTo(34);
        assertThat(out.get(b)).isEqualTo(33);
        assertThat(out.get(c)).isEqualTo(33);
    }

    @Test
    void exactMustMatchTotal() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThatThrownBy(() -> SplitCalculator.split(100, "EXACT", List.of(
                new SplitCalculator.ShareInput(a, 60L, null, null),
                new SplitCalculator.ShareInput(b, 30L, null, null))))
                .isInstanceOf(ApiException.class);
        Map<UUID, Long> ok = SplitCalculator.split(100, "EXACT", List.of(
                new SplitCalculator.ShareInput(a, 60L, null, null),
                new SplitCalculator.ShareInput(b, 40L, null, null)));
        assertThat(ok.get(a)).isEqualTo(60);
    }

    @Test
    void percentMustBe100() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, Long> out = SplitCalculator.split(1000, "PERCENT", List.of(
                new SplitCalculator.ShareInput(a, null, 4000, null),
                new SplitCalculator.ShareInput(b, null, 6000, null)));
        assertThat(out.get(a)).isEqualTo(400);
        assertThat(out.get(b)).isEqualTo(600);
    }

    @Test
    void sharesWeighted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, Long> out = SplitCalculator.split(300, "SHARES", List.of(
                new SplitCalculator.ShareInput(a, null, null, 2),
                new SplitCalculator.ShareInput(b, null, null, 1)));
        assertThat(out.get(a)).isEqualTo(200);
        assertThat(out.get(b)).isEqualTo(100);
    }
}
