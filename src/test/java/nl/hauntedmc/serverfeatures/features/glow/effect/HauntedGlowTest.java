package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HauntedGlowTest {

    @Test
    void alternatesBetweenConfiguredBrandColors() {
        HauntedGlow glow = new HauntedGlow();

        assertTrue(glow.isAnimated());
        assertEquals("hauntedmc", glow.id());
        assertEquals("serverfeatures.feature.glow.effect.hauntedmc", glow.permission());
        assertEquals(Material.GHAST_SPAWN_EGG, glow.menuMaterial());
        assertEquals(NamedTextColor.GOLD, glow.colorAt(null, 0));
        assertEquals(NamedTextColor.AQUA, glow.colorAt(null, 1));
        assertEquals(NamedTextColor.GOLD, glow.colorAt(null, 2));
    }
}

