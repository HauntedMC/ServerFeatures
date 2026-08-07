package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.persistence.EconomyRejectedException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared presentation helpers for Economy command adapters. */
final class EconomyCommandSupport {
    private static final int MAX_UNSIGNED_AMOUNT_LENGTH = 39;

    private EconomyCommandSupport() {
    }

    static String failureMessageKey(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        while (current != null) {
            if (current instanceof EconomyRejectedException rejected) return messageKey(rejected.status());
            if ("UnknownPlayerException".equals(current.getClass().getSimpleName())) return messageKey(EconomyResultStatus.UNKNOWN_PLAYER);
            if ("UnknownCurrencyException".equals(current.getClass().getSimpleName())) return messageKey(EconomyResultStatus.UNKNOWN_CURRENCY);
            if (current instanceof IllegalArgumentException) return messageKey(EconomyResultStatus.INVALID_AMOUNT);
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return messageKey(EconomyResultStatus.TEMPORARY_FAILURE);
    }

    /** Returns an end-user-safe message for a completed Economy operation. */
    static String resultMessageKey(EconomyResult result) {
        return messageKey(result == null ? EconomyResultStatus.TEMPORARY_FAILURE : result.status());
    }

    private static String messageKey(EconomyResultStatus status) {
        return status.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Rejects oversized numbers before {@link java.math.BigDecimal} parses attacker-controlled input. */
    static void requireSupportedAmountLength(String rawAmount, boolean allowsNegative) {
        int maximumLength = MAX_UNSIGNED_AMOUNT_LENGTH + (allowsNegative ? 1 : 0);
        if (rawAmount == null || rawAmount.length() > maximumLength) {
            throw new IllegalArgumentException("Amount exceeds supported decimal precision");
        }
    }

    /**
     * Parses a command amount without changing the amount the sender authorized.
     *
     * <p>Configuration values may be rounded while an administrator configures a currency, but
     * a command request must be exact. In particular, a payment of {@code 1.005} must not become
     * a debit of {@code 1.01} for a two-decimal currency.</p>
     */
    static BigDecimal parseExactAmount(String rawAmount, int fractionalDigits, boolean allowsNegative,
                                       boolean requirePositive) {
        requireSupportedAmountLength(rawAmount, allowsNegative);
        String pattern = allowsNegative ? "-?[0-9]+(?:\\.[0-9]{1,8})?" : "[0-9]+(?:\\.[0-9]{1,8})?";
        if (rawAmount == null || !rawAmount.matches(pattern)) {
            throw new IllegalArgumentException("Invalid amount");
        }
        final BigDecimal amount;
        try {
            amount = new BigDecimal(rawAmount).setScale(fractionalDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount exceeds the configured currency precision", exception);
        }
        if (requirePositive && amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (!allowsNegative && amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        return amount;
    }
}
