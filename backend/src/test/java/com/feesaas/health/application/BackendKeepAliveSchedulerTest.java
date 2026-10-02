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

    @Test
    void prefersPublicOriginOverLocalhost() {
        assertThat(BackendKeepAliveScheduler.resolvePingUrl(
                "AUTO",
                "https://feesaas.onrender.com",
                9085)).isEqualTo("https://feesaas.onrender.com/api/health/keep-alive");
        assertThat(BackendKeepAliveScheduler.resolvePingUrl(
                "https://api.example.com/",
                "https://ignored.onrender.com",
                9085)).isEqualTo("https://api.example.com/api/health/keep-alive");
        assertThat(BackendKeepAliveScheduler.resolvePingUrl(
                "https://api.example.com/api/health/keep-alive",
                null,
                9085)).isEqualTo("https://api.example.com/api/health/keep-alive");
        assertThat(BackendKeepAliveScheduler.resolvePingUrl("AUTO", null, 9085))
                .isEqualTo("http://127.0.0.1:9085/api/health/keep-alive");
    }
}
