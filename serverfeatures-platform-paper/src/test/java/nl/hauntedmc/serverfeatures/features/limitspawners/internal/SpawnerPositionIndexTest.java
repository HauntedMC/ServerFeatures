package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerPositionIndexTest {

    @AfterEach
    void clearPendingPlacements() {
        PendingSpawnerPlacements.clearAll();
    }

    @Test
    void countsThreeDimensionalRadiusAcrossChunkAndNegativeBoundaries() {
        SpawnerPositionIndex index = new SpawnerPositionIndex();
        UUID worldId = UUID.randomUUID();
        SpawnerKey center = new SpawnerKey(worldId, -1, 64, -1);
        SpawnerKey inside = new SpawnerKey(worldId, 15, 64, -1);
        SpawnerKey verticalInside = new SpawnerKey(worldId, -1, 80, -1);
        SpawnerKey outside = new SpawnerKey(worldId, 32, 64, -1);

        index.add(center);
        index.add(inside);
        index.add(verticalInside);
        index.add(outside);

        assertEquals(3, index.countWithin(center, 16));
        assertEquals(4, index.size());
        assertTrue(index.positionsInChunk(worldId, -1, -1).contains(center));
        assertTrue(index.positionsInChunk(worldId, 0, -1).contains(inside));
    }

    @Test
    void duplicateAddsAndMissingRemovesDoNotChangeState() {
        SpawnerPositionIndex index = new SpawnerPositionIndex();
        SpawnerKey spawner = new SpawnerKey(UUID.randomUUID(), 1, 64, 1);

        assertTrue(index.add(spawner));
        assertFalse(index.add(spawner));
        assertEquals(1, index.size());
        assertTrue(index.remove(spawner));
        assertFalse(index.remove(spawner));
        assertEquals(0, index.size());
    }

    @Test
    void provisionalPlacementsRemainReservedAfterCommit() {
        SpawnerPositionIndex index = new SpawnerPositionIndex();
        SpawnerKey spawner = new SpawnerKey(UUID.randomUUID(), 1, 64, 1);

        PendingSpawnerPlacements.mark(spawner);

        assertFalse(index.add(spawner));
        assertEquals(0, index.size());
        assertTrue(PendingSpawnerPlacements.contains(spawner));

        PendingSpawnerPlacements.commit(spawner);

        assertTrue(index.add(spawner));
        assertEquals(1, index.size());
        assertEquals(0, index.countWithin(spawner, 1));
        assertTrue(PendingSpawnerPlacements.contains(spawner));

        PendingSpawnerPlacements.clear(spawner);

        assertFalse(PendingSpawnerPlacements.contains(spawner));
        assertEquals(1, index.countWithin(spawner, 1));
    }

    @Test
    void cancelledPlacementsBlockIndexingUntilFinalizerCleanup() {
        SpawnerPositionIndex index = new SpawnerPositionIndex();
        SpawnerKey spawner = new SpawnerKey(UUID.randomUUID(), 1, 64, 1);

        PendingSpawnerPlacements.mark(spawner);
        PendingSpawnerPlacements.cancel(spawner);

        assertFalse(index.add(spawner));
        assertEquals(0, index.size());
        assertTrue(PendingSpawnerPlacements.contains(spawner));

        PendingSpawnerPlacements.clear(spawner);

        assertTrue(index.add(spawner));
        assertEquals(1, index.size());
    }
}
