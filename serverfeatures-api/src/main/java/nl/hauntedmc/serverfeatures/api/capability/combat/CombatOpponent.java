package nl.hauntedmc.serverfeatures.api.capability.combat;

import java.util.Objects;
import java.util.UUID;

/** Immutable platform-neutral identity of the opponent represented by a combat tag. */
public record CombatOpponent(
        UUID uniqueId,
        String entityType,
        String displayName,
        boolean player
) {
    public CombatOpponent {
        Objects.requireNonNull(uniqueId, "uniqueId");
        entityType = requireText(entityType, "entityType");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (displayName.isEmpty()) {
            displayName = entityType;
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
