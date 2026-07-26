package nl.hauntedmc.serverfeatures.features.portals.model;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTest {

    @Test
    void containsHonorsWorldAndBounds() {
        Region region = new Region("world", 10, 60, 10, 0, 70, 0);

        World sameWorld = InterfaceProxy.of(World.class, Map.of("getName", args -> "world"));
        World otherWorld = InterfaceProxy.of(World.class, Map.of("getName", args -> "other"));

        assertTrue(region.contains(new Location(sameWorld, 5, 64, 5)));
        assertFalse(region.contains(new Location(sameWorld, -1, 64, 5)));
        assertFalse(region.contains(new Location(otherWorld, 5, 64, 5)));
    }
}

