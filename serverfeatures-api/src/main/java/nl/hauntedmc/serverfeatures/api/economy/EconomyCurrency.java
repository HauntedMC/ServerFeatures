package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Objects;

/** Public immutable currency definition. */
public record EconomyCurrency(
        String id,
        String singular,
        String plural,
        String symbol,
        int fractionalDigits,
        EconomyScope scope,
        BigDecimal minimumBalance,
        BigDecimal maximumBalance,
        boolean paymentsEnabled
) {
    public EconomyCurrency {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        id = id.trim().toLowerCase(java.util.Locale.ROOT);
        singular = Objects.requireNonNullElse(singular, id);
        plural = Objects.requireNonNullElse(plural, singular);
        symbol = Objects.requireNonNullElse(symbol, "");
        if (fractionalDigits < 0 || fractionalDigits > 8) {
            throw new IllegalArgumentException("fractionalDigits must be between 0 and 8");
        }
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(minimumBalance, "minimumBalance");
        Objects.requireNonNull(maximumBalance, "maximumBalance");
    }
}
