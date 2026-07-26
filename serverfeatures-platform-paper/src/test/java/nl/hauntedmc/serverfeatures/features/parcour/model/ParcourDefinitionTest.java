package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParcourDefinitionTest {

    @Test
    void managesRegionsAndCheckpointOrders() {
        ParcourDefinition def = new ParcourDefinition("course");
        ParcourRegion start = new ParcourRegion(0, ParcourRegionType.START);
        ParcourRegion cp2 = new ParcourRegion(2, ParcourRegionType.CHECKPOINT);
        ParcourRegion cp1 = new ParcourRegion(1, ParcourRegionType.CHECKPOINT);
        ParcourRegion end = new ParcourRegion(9, ParcourRegionType.END);

        def.setStartRegion(start);
        def.setEndRegion(end);
        def.putCheckpoint(cp2);
        def.putCheckpoint(cp1);

        assertEquals(4, def.totalRegions());
        assertEquals(2, def.totalCheckpoints());
        assertEquals(java.util.Set.of(1, 2), def.orders());

        assertTrue(def.removeCheckpoint(1));
        assertFalse(def.removeCheckpoint(5));
        assertTrue(def.clearStartRegion());
        assertTrue(def.clearEndRegion());
    }

    @Test
    void putCheckpointRejectsNonCheckpointRegion() {
        ParcourDefinition def = new ParcourDefinition("course");
        ParcourRegion end = new ParcourRegion(1, ParcourRegionType.END);

        assertThrows(IllegalArgumentException.class, () -> def.putCheckpoint(end));
    }

    @Test
    void numericSettersClampToSafeRanges() {
        ParcourDefinition def = new ParcourDefinition("course");

        def.setFinishTeleportDelaySeconds(-5);
        def.setCheckpointCooldownSeconds(-1);
        def.setStartCountdownSeconds(-2);
        def.setFinishActionbarHoldMs(-10);
        def.setParticleIntervalTicks(0);
        def.setParticleOutlineTargetPoints(0);
        def.setSlotCheckpoint(80);
        def.setSlotLeave(-9);

        assertEquals(0, def.finishTeleportDelaySeconds());
        assertEquals(0, def.checkpointCooldownSeconds());
        assertEquals(0, def.startCountdownSeconds());
        assertEquals(0, def.finishActionbarHoldMs());
        assertEquals(1, def.particleIntervalTicks());
        assertEquals(1, def.particleOutlineTargetPoints());
        assertEquals(35, def.slotCheckpoint());
        assertEquals(0, def.slotLeave());
    }

    @Test
    void effectAndStartKitMutatorsNormalizeValues() {
        ParcourDefinition def = new ParcourDefinition("course");

        def.setEffect(" speed ", -3);
        assertEquals("SPEED", def.effectTypeName().orElseThrow());
        assertEquals(0, def.effectAmplifier());

        def.setEffect(null, 5);
        assertTrue(def.effectTypeName().isEmpty());
        assertEquals(0, def.effectAmplifier());

        def.addStartKitSerialized("abc");
        def.addStartKitSerialized(" ");
        def.addStartKitSerialized("def");
        assertEquals(2, def.startKitEncoded().size());
        assertTrue(def.removeStartKitIndex(2));
        assertFalse(def.removeStartKitIndex(10));
        def.clearStartKit();
        assertTrue(def.startKitEncoded().isEmpty());
    }
}

