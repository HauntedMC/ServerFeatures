package nl.hauntedmc.serverfeatures.api.graveyard;

import java.util.UUID;

/**
 * Read-only grave metadata exposed to other features and plugins.
 */
public record GraveSnapshot(
        UUID graveId,
        String shortId,
        UUID ownerUuid,
        String ownerName,
        String serverId,
        String inventoryScope,
        UUID worldUuid,
        String worldKey,
        double x,
        double y,
        double z,
        GraveStatus status,
        boolean remoteOnly,
        long remainingActiveMillis,
        int itemEntryCount,
        int remainingExperience
) {
}
