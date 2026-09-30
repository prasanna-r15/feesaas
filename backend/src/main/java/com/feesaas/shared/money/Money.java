package com.feesaas.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** Amount in minor units (e.g. paise). Never use floating point for money. */
public record Money(long minor, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money zero(String currencyCode) {
        return new Money(0, Currency.getInstance(currencyCode));
    }

    public static Money ofMinor(long minor, String currencyCode) {
        return new Money(minor, Currency.getInstance(currencyCode));
    }

    /** From a decimal major-unit amount (e.g. 1500.00). Rounds HALF_UP to the currency's precision. */
    public static Money ofMajor(BigDecimal major, String currencyCode) {
        Currency c = Currency.getInstance(currencyCode);
        int digits = Math.max(c.getDefaultFractionDigits(), 0);
        long minor = major.setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact();
        return new Money(minor, c);
    }

    public BigDecimal toMajor() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(minor, digits);
    }

    public Money plus(Money o) {
        requireSameCurrency(o);
        return new Money(Math.addExact(minor, o.minor), currency);
    }

    public Money minus(Money o) {
        requireSameCurrency(o);
        return new Money(Math.subtractExact(minor, o.minor), currency);
    }

    public Money min(Money o) { return compareTo(o) <= 0 ? this : o; }

    public boolean isZero() { return minor == 0; }
    public boolean isNegative() { return minor < 0; }
    public boolean isPositive() { return minor > 0; }

    @Override
    public int compareTo(Money o) {
        requireSameCurrency(o);
        return Long.compare(minor, o.minor);
    }

    private void requireSameCurrency(Money o) {
        if (!currency.equals(o.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + o.currency);
        }
    }
}
