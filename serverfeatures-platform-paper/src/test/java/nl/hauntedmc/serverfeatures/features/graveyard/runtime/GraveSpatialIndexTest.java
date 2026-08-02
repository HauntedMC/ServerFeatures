package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveSpatialIndexTest {
    @Test
    void indexesMovesAndRemovesGravesWithoutDuplicates() {
        UUID world = UUID.randomUUID();
        Grave grave = grave(world, 1.0, 1.0);
        GraveSpatialIndex index = new GraveSpatialIndex();

        index.put(grave);
        assertEquals(1, index.nearby(world, 0, 0, 0).size());

        grave.relocate(new GraveLocation(world, "minecraft:world", 48.0, 64.0, 48.0, 0.0f),
                GravePlacementType.NEARBY_SAFE);
        index.put(grave);
        assertTrue(index.nearby(world, 0, 0, 0).isEmpty());
        assertEquals(1, index.nearby(world, 3, 3, 0).size());

        index.remove(grave.graveId());
        assertTrue(index.nearby(world, 3, 3, 0).isEmpty());
    }

    private static Grave grave(UUID world, double x, double z) {
        GraveLocation location = new GraveLocation(world, "minecraft:world", x, 64.0, z, 0.0f);
        return new Grave(
                UUID.randomUUID(), "ABC123", UUID.randomUUID(), "Player", "survival-1", "survival",
                location, location, GravePlacementType.EXACT, GraveStatus.ACTIVE,
                1L, 1L, 60_001L, null, 1, 5, 0L, "checksum", "minecraft:generic", false
        );
    }
}
