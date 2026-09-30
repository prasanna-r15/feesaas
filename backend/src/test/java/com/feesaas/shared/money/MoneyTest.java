package com.feesaas.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void convertsMajorToMinorWithoutFloatingPointErrors() {
        assertThat(Money.ofMajor(new BigDecimal("1500.00"), "INR").minor()).isEqualTo(150_000L);
        assertThat(Money.ofMajor(new BigDecimal("0.10"), "INR").minor()).isEqualTo(10L);
        assertThat(Money.ofMajor(new BigDecimal("10.005"), "INR").minor()).isEqualTo(1_001L);   // HALF_UP
    }

    @Test
    void roundTripsToMajor() {
        assertThat(Money.ofMinor(150_050, "INR").toMajor()).isEqualByComparingTo("1500.50");
    }

    @Test
    void arithmeticAndComparison() {
        Money a = Money.ofMinor(100_000, "INR");
        Money b = Money.ofMinor(40_000, "INR");
        assertThat(a.minus(b).minor()).isEqualTo(60_000L);
        assertThat(a.plus(b).minor()).isEqualTo(140_000L);
        assertThat(a.min(b)).isEqualTo(b);
        assertThat(b.minus(a).isNegative()).isTrue();
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.ofMinor(1, "INR").plus(Money.ofMinor(1, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
