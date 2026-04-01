package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainbowGlowTest {

    @Test
    void cyclesColorsByElapsedSeconds() {
        RainbowGlow glow = new RainbowGlow();

        assertTrue(glow.isAnimated());
        assertEquals("rainbow", glow.id());
        assertEquals("serverfeatures.feature.glow.effect.rainbow", glow.permission());
        assertEquals(Material.BEACON, glow.menuMaterial());
        assertEquals(NamedTextColor.RED, glow.colorAt(null, 0));
        assertEquals(NamedTextColor.GOLD, glow.colorAt(null, 1));
        assertEquals(NamedTextColor.RED, glow.colorAt(null, 16));
    }
}

