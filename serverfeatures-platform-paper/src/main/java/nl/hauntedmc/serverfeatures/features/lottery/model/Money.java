package nl.hauntedmc.serverfeatures.features.lottery.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable two-decimal currency value used by Lottery. */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.DOWN;
    public static final Money ZERO = new Money(BigDecimal.ZERO);
    private static final BigDecimal MAXIMUM = new BigDecimal("1000000000.00");

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(SCALE, ROUNDING);
        if (amount.abs().compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("Money amount exceeds the supported maximum");
        }
    }

    public static Money parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Money value cannot be blank");
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim().replace(',', '.'));
            if (Math.max(0, parsed.stripTrailingZeros().scale()) > SCALE) {
                throw new IllegalArgumentException("Money value may contain at most two decimal places");
            }
            return new Money(parsed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid money value: " + value, exception);
        }
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public static Money fromVault(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Vault returned a non-finite amount");
        }
        return new Money(BigDecimal.valueOf(value));
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(long multiplier) {
        if (multiplier < 0L) {
            throw new IllegalArgumentException("multiplier cannot be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public Money percentage(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "percentage");
        if (percentage.signum() < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
        return new Money(amount.multiply(percentage).divide(new BigDecimal("100"), SCALE, ROUNDING));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public double toVault() {
        return amount.doubleValue();
    }

    public String plain() {
        return amount.toPlainString();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
