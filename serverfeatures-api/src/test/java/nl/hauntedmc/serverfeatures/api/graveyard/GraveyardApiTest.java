package nl.hauntedmc.serverfeatures.api.graveyard;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveyardApiTest {

    @Test
    void enumContractsExposeEverySupportedValue() {
        assertEquals(5, ClaimReason.values().length);
        assertSame(ClaimReason.PHYSICAL_INTERACTION, ClaimReason.valueOf("PHYSICAL_INTERACTION"));

        assertEquals(11, GraveClaimOutcome.values().length);
        assertSame(GraveClaimOutcome.RECOVERY_PENDING, GraveClaimOutcome.valueOf("RECOVERY_PENDING"));

        assertEquals(9, GraveStatus.values().length);
        assertSame(GraveStatus.DELIVERY_PENDING, GraveStatus.valueOf("DELIVERY_PENDING"));
    }

    @Test
    void statusCapabilitiesMatchTheLifecycleContract() {
        assertTrue(GraveStatus.ACTIVE.isVisible());
        assertTrue(GraveStatus.PARTIAL.isVisible());
        assertFalse(GraveStatus.EXPIRED.isVisible());

        assertTrue(GraveStatus.ACTIVE.isPlayerClaimable());
        assertTrue(GraveStatus.PARTIAL.isPlayerClaimable());
        assertTrue(GraveStatus.ORPHANED_WORLD.isPlayerClaimable());
        assertFalse(GraveStatus.DELIVERY_PENDING.isPlayerClaimable());

        assertTrue(GraveStatus.CORRUPT.hasRecoverablePayload());
        assertFalse(GraveStatus.CLAIMED.hasRecoverablePayload());
        assertFalse(GraveStatus.ADMIN_RECOVERED.hasRecoverablePayload());
        assertFalse(GraveStatus.PURGED.hasRecoverablePayload());
    }

    @Test
    void immutableResultAndSnapshotPreserveTheirValues() {
        UUID graveId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        GraveClaimResult result = new GraveClaimResult(
                graveId,
                GraveClaimOutcome.PARTIAL,
                3,
                2,
                17,
                "partial"
        );
        GraveSnapshot snapshot = new GraveSnapshot(
                graveId,
                "remymine-world-20260804-002600-000",
                ownerId,
                "remymine",
                "demo",
                "demo",
                worldId,
                "minecraft:world",
                10.5,
                64.0,
                -20.5,
                GraveStatus.PARTIAL,
                false,
                60_000L,
                2,
                17
        );

        assertEquals(graveId, result.graveId());
        assertEquals(GraveClaimOutcome.PARTIAL, result.outcome());
        assertEquals(3, result.transferredEntries());
        assertEquals(2, result.remainingEntries());
        assertEquals(17, result.transferredExperience());
        assertEquals("partial", result.message());

        assertEquals(graveId, snapshot.graveId());
        assertEquals("remymine-world-20260804-002600-000", snapshot.shortId());
        assertEquals(ownerId, snapshot.ownerUuid());
        assertEquals("remymine", snapshot.ownerName());
        assertEquals("demo", snapshot.serverId());
        assertEquals("demo", snapshot.inventoryScope());
        assertEquals(worldId, snapshot.worldUuid());
        assertEquals("minecraft:world", snapshot.worldKey());
        assertEquals(10.5, snapshot.x());
        assertEquals(64.0, snapshot.y());
        assertEquals(-20.5, snapshot.z());
        assertEquals(GraveStatus.PARTIAL, snapshot.status());
        assertFalse(snapshot.remoteOnly());
        assertEquals(60_000L, snapshot.remainingActiveMillis());
        assertEquals(2, snapshot.itemEntryCount());
        assertEquals(17, snapshot.remainingExperience());
    }
}
