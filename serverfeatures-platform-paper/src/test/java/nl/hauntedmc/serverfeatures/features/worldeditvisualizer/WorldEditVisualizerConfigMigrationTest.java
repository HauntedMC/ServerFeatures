package nl.hauntedmc.serverfeatures.features.worldeditvisualizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldEditVisualizerConfigMigrationTest {

    @Test
    void keepsValidWholeBlockIntervals() {
        assertNull(WorldEditVisualizer.normalizeStep(1));
        assertNull(WorldEditVisualizer.normalizeStep(64L));
    }

    @Test
    void raisesLegacyFractionalIntervalsToWholeBlocks() {
        assertEquals(1, WorldEditVisualizer.normalizeStep(0.25));
        assertEquals(2, WorldEditVisualizer.normalizeStep(1.01));
        assertEquals(3, WorldEditVisualizer.normalizeStep(2.5));
    }

    @Test
    void clampsInvalidNumericIntervals() {
        assertEquals(1, WorldEditVisualizer.normalizeStep(-5));
        assertEquals(1, WorldEditVisualizer.normalizeStep(Double.NaN));
        assertEquals(Integer.MAX_VALUE,
                WorldEditVisualizer.normalizeStep((double) Integer.MAX_VALUE + 100.0));
    }

    @Test
    void leavesNonNumericValuesForSchemaReconciliation() {
        assertNull(WorldEditVisualizer.normalizeStep(null));
        assertNull(WorldEditVisualizer.normalizeStep("2"));
    }
}
