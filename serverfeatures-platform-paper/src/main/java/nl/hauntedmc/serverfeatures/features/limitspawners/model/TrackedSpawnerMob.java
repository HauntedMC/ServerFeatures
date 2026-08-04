package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import org.bukkit.entity.EntityType;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-only source attribution and maintenance state for one active spawner mob.
 */
public record TrackedSpawnerMob(
        UUID entityId,
        SpawnerKey spawner,
        EntityType entityType,
        long spawnedAtMillis,
        EntityChunkKey entityChunk,
        Long outsideSinceMillis
) {

    public TrackedSpawnerMob {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(spawner, "spawner");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityChunk, "entityChunk");
    }

    public TrackedSpawnerMob relocate(EntityChunkKey destination) {
        Objects.requireNonNull(destination, "destination");
        if (entityChunk.equals(destination)) {
            return this;
        }
        return new TrackedSpawnerMob(
                entityId,
                spawner,
                entityType,
                spawnedAtMillis,
                destination,
                outsideSinceMillis
        );
    }

    public TrackedSpawnerMob outsideSince(long timestamp) {
        if (outsideSinceMillis != null) {
            return this;
        }
        return new TrackedSpawnerMob(
                entityId,
                spawner,
                entityType,
                spawnedAtMillis,
                entityChunk,
                timestamp
        );
    }

    public TrackedSpawnerMob clearOutsideSince() {
        if (outsideSinceMillis == null) {
            return this;
        }
        return new TrackedSpawnerMob(
                entityId,
                spawner,
                entityType,
                spawnedAtMillis,
                entityChunk,
                null
        );
    }
}
