package nl.hauntedmc.serverfeatures.features.graveyard.config;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraveyardSettingsTest {
    @Test
    void legacySoundNamesPreserveUnderscoresInsideRegistryPaths() {
        assertEquals(
                "BLOCK_RESPAWN_ANCHOR_CHARGE",
                GraveyardSettings.legacySoundName(NamespacedKey.minecraft("block.respawn_anchor.charge"))
        );
        assertEquals(
                "PARTICLE_SOUL_ESCAPE",
                GraveyardSettings.legacySoundName(NamespacedKey.minecraft("particle.soul_escape"))
        );
    }
}
