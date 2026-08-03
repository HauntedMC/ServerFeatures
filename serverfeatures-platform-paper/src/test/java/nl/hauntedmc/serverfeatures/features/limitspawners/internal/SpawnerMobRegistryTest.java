package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerMobRegistryTest {

    @Test
    void tracksIndependentSpawnerAreaWorldAndServerCounts() {
        SpawnerMobRegistry registry = new SpawnerMobRegistry();
        UUID worldId = UUID.randomUUID();
        SpawnerKey firstSpawner = new SpawnerKey(worldId, 1, 64, 1);
        SpawnerKey nearbySpawner = new SpawnerKey(worldId, 20, 64, 1);
        SpawnerKey distantSpawner = new SpawnerKey(worldId, 100, 64, 1);

        registry.put(record(UUID.randomUUID(), firstSpawner, new EntityChunkKey(worldId, 0, 0)));
        registry.put(record(UUID.randomUUID(), firstSpawner, new EntityChunkKey(worldId, 0, 0)));
        registry.put(record(UUID.randomUUID(), nearbySpawner, new EntityChunkKey(worldId, 1, 0)));
        registry.put(record(UUID.randomUUID(), distantSpawner, new EntityChunkKey(worldId, 6, 0)));

        assertEquals(2, registry.count(firstSpawner));
        assertEquals(3, registry.countInArea(firstSpawner, 32));
        assertEquals(4, registry.worldCount(worldId));
        assertEquals(4, registry.size());
    }

    @Test
    void replacingAnEntityUpdatesEveryIndexWithoutChangingWorldCount() {
        SpawnerMobRegistry registry = new SpawnerMobRegistry();
        UUID entityId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        SpawnerKey originalSpawner = new SpawnerKey(worldId, 1, 64, 1);
        SpawnerKey replacementSpawner = new SpawnerKey(worldId, 48, 64, 48);
        EntityChunkKey originalChunk = new EntityChunkKey(worldId, 0, 0);
        EntityChunkKey replacementChunk = new EntityChunkKey(worldId, 3, 3);

        registry.put(record(entityId, originalSpawner, originalChunk));
        registry.put(record(entityId, replacementSpawner, replacementChunk));

        assertEquals(0, registry.count(originalSpawner));
        assertEquals(1, registry.count(replacementSpawner));
        assertFalse(registry.entityIdsInMobChunk(originalChunk).contains(entityId));
        assertTrue(registry.entityIdsInMobChunk(replacementChunk).contains(entityId));
        assertFalse(registry.spawnersInSourceChunk(originalSpawner.chunkKey())
                .contains(originalSpawner));
        assertTrue(registry.spawnersInSourceChunk(replacementSpawner.chunkKey())
                .contains(replacementSpawner));
        assertEquals(1, registry.worldCount(worldId));
    }

    @Test
    void removeCleansEveryIndexAndSnapshotIsStable() {
        SpawnerMobRegistry registry = new SpawnerMobRegistry();
        UUID worldId = UUID.randomUUID();
        SpawnerKey spawner = new SpawnerKey(worldId, 1, 64, 1);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EntityChunkKey chunk = new EntityChunkKey(worldId, 0, 0);

        registry.put(record(first, spawner, chunk));
        registry.put(record(second, spawner, chunk));

        assertEquals(second, registry.snapshot().getFirst().entityId());
        assertTrue(registry.remove(first).isPresent());
        assertFalse(registry.contains(first));
        assertEquals(1, registry.count(spawner));
        assertEquals(1, registry.entityIdsInMobChunk(chunk).size());
        assertEquals(1, registry.worldCount(worldId));

        assertTrue(registry.remove(second).isPresent());
        assertTrue(registry.spawnersInSourceChunk(spawner.chunkKey()).isEmpty());
        assertEquals(0, registry.worldCount(worldId));
    }

    private static TrackedSpawnerMob record(
            UUID entityId,
            SpawnerKey spawner,
            EntityChunkKey chunk
    ) {
        return new TrackedSpawnerMob(
                entityId,
                spawner,
                EntityType.ZOMBIE,
                1L,
                chunk,
                null
        );
    }
}
