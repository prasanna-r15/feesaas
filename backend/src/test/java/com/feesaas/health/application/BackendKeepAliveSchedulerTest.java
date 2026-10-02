package com.feesaas.health.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackendKeepAliveSchedulerTest {

    @Test
    void acceptsPositiveIntervalsUpToADay() {
        assertThat(BackendKeepAliveScheduler.parseMinutes("10")).isEqualTo(10);
        assertThat(BackendKeepAliveScheduler.parseMinutes(" 1 ")).isEqualTo(1);
        assertThat(BackendKeepAliveScheduler.parseMinutes("1440")).isEqualTo(1440);
        assertThat(BackendKeepAliveScheduler.parseMinutes(null)).isEqualTo(10);
        assertThat(BackendKeepAliveScheduler.parseMinutes("")).isEqualTo(10);
    }

    @Test
    void rejectsZeroNegativeAndNonNumericIntervals() {
        assertThat(BackendKeepAliveScheduler.parseMinutes("0")).isNull();
        assertThat(BackendKeepAliveScheduler.parseMinutes("-5")).isNull();
        assertThat(BackendKeepAliveScheduler.parseMinutes("1441")).isNull();
        assertThat(BackendKeepAliveScheduler.parseMinutes("ten")).isNull();
    }

    @Test
    void parsesEnabledFlags() {
        assertThat(BackendKeepAliveScheduler.parseEnabled("true")).isTrue();
        assertThat(BackendKeepAliveScheduler.parseEnabled("TRUE")).isTrue();
        assertThat(BackendKeepAliveScheduler.parseEnabled("on")).isTrue();
        assertThat(BackendKeepAliveScheduler.parseEnabled("false")).isFalse();
        assertThat(BackendKeepAliveScheduler.parseEnabled("0")).isFalse();
        assertThat(BackendKeepAliveScheduler.parseEnabled("off")).isFalse();
        assertThat(BackendKeepAliveScheduler.parseEnabled(null)).isTrue();
    }
}
