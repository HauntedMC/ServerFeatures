package nl.hauntedmc.serverfeatures.api.combat;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable view of an active combat tag.
 */
public record CombatTagSnapshot(
        UUID playerId,
        CombatOpponent opponent,
        CombatTagReason reason,
        Instant taggedAt,
        Instant expiresAt,
        Duration remaining
) {
    public CombatTagSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(taggedAt, "taggedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(remaining, "remaining");
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }
    }
}
