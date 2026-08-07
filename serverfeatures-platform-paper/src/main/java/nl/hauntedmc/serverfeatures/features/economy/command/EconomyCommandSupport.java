package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;

/** Shared presentation helpers for Economy command adapters. */
final class EconomyCommandSupport {
    private static final String TEMPORARY_FAILURE = "The economy service is temporarily unavailable";
    private static final int MAX_UNSIGNED_AMOUNT_LENGTH = 39;

    private EconomyCommandSupport() {
    }

    static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        if (current instanceof IllegalArgumentException) {
            String message = current.getMessage();
            return message == null || message.isBlank() ? "Invalid request" : message;
        }
        // Database, messaging, and framework exceptions can disclose implementation details.
        return TEMPORARY_FAILURE;
    }

    /** Returns an end-user-safe message for a completed Economy operation. */
    static String resultMessage(EconomyResult result) {
        if (result == null || result.status() == EconomyResultStatus.TEMPORARY_FAILURE) {
            return TEMPORARY_FAILURE;
        }
        String message = result.message();
        return message == null || message.isBlank() ? "The economy operation was rejected" : message;
    }

    /** Rejects oversized numbers before {@link java.math.BigDecimal} parses attacker-controlled input. */
    static void requireSupportedAmountLength(String rawAmount, boolean allowsNegative) {
        int maximumLength = MAX_UNSIGNED_AMOUNT_LENGTH + (allowsNegative ? 1 : 0);
        if (rawAmount == null || rawAmount.length() > maximumLength) {
            throw new IllegalArgumentException("Amount exceeds supported decimal precision");
        }
    }
}
