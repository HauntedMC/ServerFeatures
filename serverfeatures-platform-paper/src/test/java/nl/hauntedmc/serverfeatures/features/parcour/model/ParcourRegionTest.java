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

class ParcourRegionTest {

    @Test
    void commandsStripLeadingSlashAndIgnoreBlank() {
        ParcourRegion region = new ParcourRegion(1, ParcourRegionType.CHECKPOINT);

        region.addCommand("/say hi");
        region.addCommand("  ");
        region.addCommand("title @p test");

        assertEquals(2, region.commands().size());
        assertEquals("say hi", region.commands().getFirst());
        assertEquals("title @p test", region.commands().get(1));
    }

    @Test
    void endRegionNeverEnablesRestoreCheckpoint() {
        ParcourRegion region = new ParcourRegion(99, ParcourRegionType.END);

        region.setRestoreCheckpoint(true);

        assertFalse(region.restoreCheckpoint());
    }

    @Test
    void explicitRestoreCanBeResolvedViaServerWorld() {
        ParcourRegion region = new ParcourRegion(2, ParcourRegionType.CHECKPOINT);
        region.setExplicitRestore("world", 1.5D, 70.0D, -3.0D, 90.0F, 10.0F);

        World world = InterfaceProxy.of(World.class, Map.of("getName", args -> "world"));
        Server server = InterfaceProxy.of(Server.class, Map.of("getWorld", args -> world));
        Location restore = region.explicitRestore(server).orElseThrow();

        assertEquals(1.5D, restore.getX());
        assertEquals(70.0D, restore.getY());
        assertEquals(-3.0D, restore.getZ());
    }

    @Test
    void resolveRestoreFallsBackToRegionCenterWhenNoExplicitRestore() {
        ParcourRegion region = new ParcourRegion(3, ParcourRegionType.CHECKPOINT);
        region.setRegion(new Region("world", 0, 60, 0, 2, 62, 2));

        World world = InterfaceProxy.of(World.class, Map.of("getName", args -> "world"));
        Server server = InterfaceProxy.of(Server.class, Map.of("getWorld", args -> world));
        Location resolved = region.resolveRestoreLocation(server);

        assertNotNull(resolved);
        assertEquals(1.5D, resolved.getX());
        assertEquals(61.0D, resolved.getY());
        assertEquals(1.5D, resolved.getZ());
    }
}
