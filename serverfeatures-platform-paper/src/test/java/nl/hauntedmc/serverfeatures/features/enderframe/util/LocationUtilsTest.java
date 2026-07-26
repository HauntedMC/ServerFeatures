package nl.hauntedmc.serverfeatures.features.enderframe.util;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocationUtilsTest {

    @Test
    void returnsFalseOutsideOverworldWithoutStructureLookup() {
        AtomicInteger lookups = new AtomicInteger();
        World world = InterfaceProxy.of(World.class, Map.of(
                "getEnvironment", args -> World.Environment.NETHER,
                "hasStructureAt", args -> {
                    lookups.incrementAndGet();
                    return true;
                }
        ));
        Block block = blockAt(world);

        assertFalse(LocationUtils.isInStronghold(block));
        assertEquals(0, lookups.get());
    }

    private static Block blockAt(World world) {
        return InterfaceProxy.of(Block.class, Map.of(
                "getWorld", args -> world,
                "getLocation", args -> new Location(world, 10, 64, 10)
        ));
    }
}
