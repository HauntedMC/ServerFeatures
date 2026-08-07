package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds short-lived player payment confirmations independently from command parsing.
 *
 * <p>Every payment attempt invalidates the previous one. The token returned by
 * {@link #begin(UUID)} prevents an older asynchronous recipient lookup from replacing a newer
 * confirmation. Entries are removed when consumed and periodically pruned after their TTL.</p>
 */
final class PaymentConfirmationTracker {
    static final long CONFIRMATION_TTL_MILLIS = 30_000L;

    private final Map<UUID, PendingConfirmation> confirmations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingResolution> resolutions = new ConcurrentHashMap<>();

    /** Invalidates the player's previous payment attempt and returns a token for the new one. */
    UUID begin(UUID playerUuid) {
        pruneExpired();
        confirmations.remove(playerUuid);
        UUID token = UUID.randomUUID();
        resolutions.put(playerUuid, new PendingResolution(token, System.currentTimeMillis()));
        return token;
    }

    /** Stores a confirmation only when it still belongs to the player's most recent attempt. */
    boolean confirm(UUID playerUuid, UUID token, Identity recipient, BigDecimal amount) {
        PendingResolution resolution = resolutions.get(playerUuid);
        if (resolution == null || !resolution.token().equals(token)
                || isExpired(resolution.createdAt())) {
            return false;
        }
        resolutions.remove(playerUuid, resolution);
        confirmations.put(playerUuid, new PendingConfirmation(recipient, amount, System.currentTimeMillis()));
        return true;
    }

    /** Consumes a still-valid confirmation and invalidates any unfinished recipient lookup. */
    Optional<PendingPayment> consume(UUID playerUuid) {
        resolutions.remove(playerUuid);
        PendingConfirmation pending = confirmations.remove(playerUuid);
        if (pending == null || isExpired(pending.createdAt())) {
            return Optional.empty();
        }
        return Optional.of(new PendingPayment(pending.recipient(), pending.amount()));
    }

    /** Removes expired entries; the owning payment handler schedules this at the confirmation TTL. */
    void pruneExpired() {
        confirmations.entrySet().removeIf(entry -> isExpired(entry.getValue().createdAt()));
        resolutions.entrySet().removeIf(entry -> isExpired(entry.getValue().createdAt()));
    }

    private static boolean isExpired(long createdAt) {
        return System.currentTimeMillis() - createdAt > CONFIRMATION_TTL_MILLIS;
    }

    record PendingPayment(Identity recipient, BigDecimal amount) {
    }

    private record PendingConfirmation(Identity recipient, BigDecimal amount, long createdAt) {
    }

    private record PendingResolution(UUID token, long createdAt) {
    }
}
