package nl.hauntedmc.serverfeatures.features.parcour.model;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTest {

    @Test
    void containsChecksWorldAndBounds() {
        Region region = new Region("parkour", 10, 70, 10, 0, 60, 0);
        World world = InterfaceProxy.of(World.class, Map.of("getName", args -> "parkour"));
        World other = InterfaceProxy.of(World.class, Map.of("getName", args -> "other"));

        assertTrue(region.contains(new Location(world, 5, 65, 5)));
        assertFalse(region.contains(new Location(world, 11, 65, 5)));
        assertFalse(region.contains(new Location(other, 5, 65, 5)));
    }

    @Test
    void centerUsesResolvedWorldFromServer() {
        Region region = new Region("parkour", 0, 60, 0, 10, 80, 10);
        World world = InterfaceProxy.of(World.class, Map.of("getName", args -> "parkour"));
        Server server = InterfaceProxy.of(Server.class, Map.of("getWorld", args -> world));

        Location center = region.center(server);

        assertNotNull(center);
        assertEquals(5.5D, center.getX());
        assertEquals(61.0D, center.getY());
        assertEquals(5.5D, center.getZ());
    }
}

