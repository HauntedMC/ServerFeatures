package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StaticColorGlowTest {

    @Test
    void staticColorEffectExposesDeterministicMetadata() {
        StaticColorGlow glow = new StaticColorGlow(NamedTextColor.RED);

        assertEquals("red", glow.id());
        assertEquals("serverfeatures.feature.glow.effect.red", glow.permission());
        assertEquals(NamedTextColor.RED, glow.colorAt(null, 0));
        assertEquals(Material.RED_CONCRETE, glow.menuMaterial());
        assertEquals(Component.text("Red"), glow.displayName(null));
        assertFalse(glow.isAnimated());
    }
}

