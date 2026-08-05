package nl.hauntedmc.serverfeatures.api.combat;

import org.bukkit.entity.EntityType;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identity of the entity currently associated with a player's combat tag.
 */
public record CombatOpponent(
        UUID uniqueId,
        EntityType entityType,
        String displayName,
        boolean player
) {
    public CombatOpponent {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(entityType, "entityType");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (displayName.isEmpty()) {
            displayName = entityType.name();
        }
    }
}
