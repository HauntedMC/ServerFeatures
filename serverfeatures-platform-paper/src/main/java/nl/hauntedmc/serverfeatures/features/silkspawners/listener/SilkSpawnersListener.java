package nl.hauntedmc.serverfeatures.features.silkspawners.listener;

import nl.hauntedmc.serverfeatures.features.silkspawners.SilkSpawners;
import nl.hauntedmc.serverfeatures.features.silkspawners.internal.SilkSpawnersHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class SilkSpawnersListener implements Listener {

    private final SilkSpawnersHandler handler;

    public SilkSpawnersListener(SilkSpawners feature) {
        this.handler = feature.getHandler();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handler.handleSpawnerBreak(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handler.handleSpawnerPlace(event);
    }
}
