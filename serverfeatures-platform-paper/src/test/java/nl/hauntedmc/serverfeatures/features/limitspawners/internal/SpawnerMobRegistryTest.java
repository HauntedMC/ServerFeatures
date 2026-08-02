package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerMobRegistryTest {

    @Test
    void tracksIndependentCountsPerExactSpawner() {
        SpawnerMobRegistry registry = new SpawnerMobRegistry();
        UUID worldId = UUID.randomUUID();
        SpawnerKey firstSpawner = new SpawnerKey(worldId, 1, 64, 1);
        SpawnerKey secondSpawner = new SpawnerKey(worldId, 2, 64, 1);

        registry.put(record(UUID.randomUUID(), firstSpawner, worldId, 0, 0));
        registry.put(record(UUID.randomUUID(), firstSpawner, worldId, 0, 0));
        registry.put(record(UUID.randomUUID(), secondSpawner, worldId, 0, 0));

        assertEquals(2, registry.count(firstSpawner));
        assertEquals(1, registry.count(secondSpawner));
        assertEquals(3, registry.size());
    }

    @Test
    void replacingAnEntityUpdatesSpawnerAndChunkIndexes() {
        SpawnerMobRegistry registry = new SpawnerMobRegistry();
        UUID entityId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        SpawnerKey originalSpawner = new SpawnerKey(worldId, 1, 64, 1);
        SpawnerKey replacementSpawner = new SpawnerKey(worldId, 48, 64, 48);
        EntityChunkKey originalChunk = new EntityChunkKey(worldId, 0, 0);
        EntityChunkKey replacementChunk = new EntityChunkKey(worldId, 3, 3);

        registry.put(record(entityId, originalSpawner, worldId, 0, 0));
        registry.put(record(entityId, replacementSpawner, worldId, 3, 3));

        assertEquals(0, registry.count(originalSpawner));
        assertEquals(1, registry.count(replacementSpawner));
        assertFalse(registry.entityIdsInChunk(originalChunk).contains(entityId));
        assertTrue(registry.entityIdsInChunk(replacementChunk).contains(entityId));
    }

    @Test
    void removeCleansEveryIndexAndSnapshotIsStable() {
        SpawnerMobRegistry registry = new SpawnerMobRegistry();
        UUID worldId = UUID.randomUUID();
        SpawnerKey spawner = new SpawnerKey(worldId, 1, 64, 1);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");

        registry.load(List.of(
                record(first, spawner, worldId, 0, 0),
                record(second, spawner, worldId, 0, 0)
        ));

        assertEquals(second, registry.snapshot().getFirst().entityId());
        assertTrue(registry.remove(first).isPresent());
        assertFalse(registry.contains(first));
        assertEquals(1, registry.count(spawner));
        assertEquals(1, registry.entityIdsInChunk(new EntityChunkKey(worldId, 0, 0)).size());
    }

    private static TrackedSpawnerMob record(
            UUID entityId,
            SpawnerKey spawner,
            UUID worldId,
            int chunkX,
            int chunkZ
    ) {
        return new TrackedSpawnerMob(entityId, spawner, worldId, chunkX, chunkZ);
    }
}
