package nl.hauntedmc.serverfeatures.features.graveyard.model;

import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraveTest {
    @Test
    void resumesPartialOrphanWithoutResettingItsRemainingLifetime() {
        Grave grave = grave(2L, GraveStatus.ORPHANED_WORLD, 12_345L);

        grave.resume(50_000L);

        assertEquals(GraveStatus.PARTIAL, grave.status());
        assertEquals(62_345L, grave.expiresActiveMillis());
        assertNull(grave.pausedRemainingMillis());
    }

    @Test
    void resumesEmptyOrphanAsClaimed() {
        Grave grave = grave(1L, GraveStatus.ORPHANED_WORLD, 1_000L);
        grave.updatePayload(new GravePayload(2L, java.util.List.of(), 0), "empty", GraveStatus.ORPHANED_WORLD);
        grave.pause(1_000L, GraveStatus.ORPHANED_WORLD);

        grave.resume(20_000L);

        assertEquals(GraveStatus.CLAIMED, grave.status());
    }

    private static Grave grave(long payloadRevision, GraveStatus status, Long pausedRemaining) {
        UUID world = UUID.randomUUID();
        GraveLocation location = new GraveLocation(world, "minecraft:world", 1, 64, 1, 0);
        return new Grave(
                UUID.randomUUID(), "ABC123DEF456", UUID.randomUUID(), "Player", "survival-1", "survival",
                location, location, GravePlacementType.DEATH_LOCATION, status,
                1L, 2L, 3L, pausedRemaining, 1, 5, payloadRevision, "checksum", "minecraft:fall", false
        );
    }
}
