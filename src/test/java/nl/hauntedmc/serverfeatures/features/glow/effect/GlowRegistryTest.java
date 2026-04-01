package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlowRegistryTest {

    @Test
    void containsDefaultEffectsAndSupportsCaseInsensitiveLookup() {
        GlowRegistry registry = new GlowRegistry();

        assertTrue(registry.all().size() >= 18);
        assertTrue(registry.find("RAINBOW").isPresent());
        assertTrue(registry.find("hauntedmc").isPresent());
    }

    @Test
    void registerOverridesByLowercasedId() {
        GlowRegistry registry = new GlowRegistry();
        GlowEffect custom = new StubGlow("Custom");

        registry.register(custom);

        assertEquals(custom, registry.find("custom").orElseThrow());
        assertEquals(custom, registry.find("CUSTOM").orElseThrow());
    }

    @Test
    void registerAllAddsMultipleEffectsInOrder() {
        GlowRegistry registry = new GlowRegistry();
        GlowEffect first = new StubGlow("first");
        GlowEffect second = new StubGlow("second");

        registry.registerAll(List.of(first, second));

        List<GlowEffect> all = registry.all();
        assertEquals(first, all.get(all.size() - 2));
        assertEquals(second, all.getLast());
    }

    private record StubGlow(String id) implements GlowEffect {
        @Override
        public Component displayName(org.bukkit.entity.Player viewer) {
            return Component.text(id);
        }

        @Override
        public String permission() {
            return "perm." + id;
        }

        @Override
        public NamedTextColor colorAt(org.bukkit.entity.Player player, long elapsedSeconds) {
            return NamedTextColor.WHITE;
        }

        @Override
        public Material menuMaterial() {
            return Material.STONE;
        }
    }
}

