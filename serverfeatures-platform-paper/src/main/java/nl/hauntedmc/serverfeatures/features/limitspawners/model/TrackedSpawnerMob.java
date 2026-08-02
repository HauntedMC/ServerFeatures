package nl.hauntedmc.serverfeatures.features.limitspawners.model;

import java.util.Objects;
import java.util.UUID;

public record TrackedSpawnerMob(
        UUID entityId,
        SpawnerKey spawner,
        UUID entityWorldId,
        int entityChunkX,
        int entityChunkZ
) {

    public TrackedSpawnerMob {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(spawner, "spawner");
        Objects.requireNonNull(entityWorldId, "entityWorldId");
    }

    public EntityChunkKey entityChunk() {
        return new EntityChunkKey(entityWorldId, entityChunkX, entityChunkZ);
    }

    public TrackedSpawnerMob relocate(EntityChunkKey destination) {
        Objects.requireNonNull(destination, "destination");
        if (entityChunk().equals(destination)) {
            return this;
        }
        return new TrackedSpawnerMob(
                entityId,
                spawner,
                destination.worldId(),
                destination.x(),
                destination.z()
        );
    }
}
