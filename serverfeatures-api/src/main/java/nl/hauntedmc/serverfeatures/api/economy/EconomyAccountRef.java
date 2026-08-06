package nl.hauntedmc.serverfeatures.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Canonical account reference accepted by the native economy API. */
public record EconomyAccountRef(
        Long playerId,
        UUID playerUuid,
        String playerName,
        String currencyId,
        String scopeKey
) {
    public EconomyAccountRef {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (currencyId == null || currencyId.isBlank()) {
            throw new IllegalArgumentException("currencyId must not be blank");
        }
        currencyId = currencyId.trim().toLowerCase(java.util.Locale.ROOT);
        if (currencyId.length() > 64) {
            throw new IllegalArgumentException("currencyId must not exceed 64 characters");
        }
        playerName = playerName == null ? "" : playerName.trim();
        scopeKey = scopeKey == null || scopeKey.isBlank() ? null : scopeKey.trim();
        if (scopeKey != null && scopeKey.length() > 128) {
            throw new IllegalArgumentException("scopeKey must not exceed 128 characters");
        }
    }
}
