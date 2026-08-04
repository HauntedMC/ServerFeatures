package nl.hauntedmc.serverfeatures.features.graveyard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceRecoveryModeTest {
    @Test
    void nativeModePreservesTheFinalMinecraftDrop() {
        assertEquals(0, ExperienceRecoveryMode.NATIVE.capturedExperience(-1, 50));
        assertEquals(0, ExperienceRecoveryMode.NATIVE.capturedExperience(0, 50));
        assertEquals(37, ExperienceRecoveryMode.NATIVE.capturedExperience(37, 0));
    }

    @Test
    void percentageModeUsesAClampedFloorPercentage() {
        assertEquals(0, ExperienceRecoveryMode.PERCENTAGE.capturedExperience(37, -10));
        assertEquals(18, ExperienceRecoveryMode.PERCENTAGE.capturedExperience(37, 50));
        assertEquals(37, ExperienceRecoveryMode.PERCENTAGE.capturedExperience(37, 100));
        assertEquals(37, ExperienceRecoveryMode.PERCENTAGE.capturedExperience(37, 150));
    }
}
