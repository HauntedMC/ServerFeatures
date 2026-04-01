package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportBoundsTest {

    @Test
    void rectUtilityMethodsNormalizeContainAndIntersectCorrectly() {
        TeleportBounds.Rect a = new TeleportBounds.Rect(10, 1, 5, -2).normalized();
        TeleportBounds.Rect b = new TeleportBounds.Rect(3, 6, -1, 2);

        assertEquals(new TeleportBounds.Rect(1, 10, -2, 5), a);
        assertTrue(a.contains(2, -1));
        assertTrue(a.encloses(b));
        assertEquals(new TeleportBounds.Rect(3, 6, -1, 2), a.intersect(b));
    }

    @Test
    void configuredOuterMustEncloseInnerOtherwiseIgnored() {
        Map<String, Object> config = new HashMap<>();
        config.put("bounds", Map.of(
                "inner", Map.of("min_x", 0, "max_x", 5, "min_z", 0, "max_z", 5),
                "outer", Map.of("min_x", 1, "max_x", 4, "min_z", 1, "max_z", 4)
        ));

        TeleportBounds bounds = new TeleportBounds(config::get);

        assertTrue(bounds.innerRect().isPresent());
        assertTrue(bounds.configuredOuterRect().isEmpty());
    }

    @Test
    void dottedPathFallbackIsUsedWhenNestedMapIsMissing() {
        Map<String, Object> config = new HashMap<>();
        config.put("bounds.inner.min_x", -10);
        config.put("bounds.inner.max_x", 10);
        config.put("bounds.inner.min_z", -8);
        config.put("bounds.inner.max_z", 8);

        TeleportBounds bounds = new TeleportBounds(config::get);
        TeleportBounds.Rect inner = bounds.innerRect().orElseThrow();

        assertEquals(-10, inner.minX());
        assertEquals(10, inner.maxX());
        assertEquals(-8, inner.minZ());
        assertEquals(8, inner.maxZ());
    }

    @Test
    void effectiveOuterIntersectsConfiguredBoundsWithWorldBorder() {
        Map<String, Object> config = new HashMap<>();
        config.put("respect_world_border", true);
        config.put("bounds", Map.of(
                "outer", Map.of("min_x", -20, "max_x", 20, "min_z", -20, "max_z", 20)
        ));

        TeleportBounds bounds = new TeleportBounds(config::get);
        World world = worldWithBorder(0.0D, 0.0D, 10.0D);

        TeleportBounds.Rect effective = bounds.effectiveOuterRect(world);

        assertEquals(new TeleportBounds.Rect(-5, 5, -5, 5), effective);
        assertTrue(bounds.withinEffectiveOuter(world, 4, -4));
        assertFalse(bounds.withinEffectiveOuter(world, 7, 0));
    }

    private static World worldWithBorder(double centerX, double centerZ, double size) {
        WorldBorder border = InterfaceProxy.of(WorldBorder.class, Map.of(
                "getCenter", args -> new Location(null, centerX, 0.0D, centerZ),
                "getSize", args -> size
        ));
        return InterfaceProxy.of(World.class, Map.of("getWorldBorder", args -> border));
    }
}

