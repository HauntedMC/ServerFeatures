package nl.hauntedmc.serverfeatures.features.bettercoral.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;

import java.util.EnumSet;
import java.util.Set;

public final class BetterCoralListener implements Listener {

    private static final Set<Material> LIVE_CORALS = EnumSet.of(
            // Blokken
            Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK,
            Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
            // Planten
            Material.TUBE_CORAL, Material.BRAIN_CORAL, Material.BUBBLE_CORAL,
            Material.FIRE_CORAL, Material.HORN_CORAL,
            // Fans
            Material.TUBE_CORAL_FAN, Material.BRAIN_CORAL_FAN, Material.BUBBLE_CORAL_FAN,
            Material.FIRE_CORAL_FAN, Material.HORN_CORAL_FAN,
            // Wall fans
            Material.TUBE_CORAL_WALL_FAN, Material.BRAIN_CORAL_WALL_FAN, Material.BUBBLE_CORAL_WALL_FAN,
            Material.FIRE_CORAL_WALL_FAN, Material.HORN_CORAL_WALL_FAN
    );


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoralFade(BlockFadeEvent event) {
        Material type = event.getBlock().getType();
        if (LIVE_CORALS.contains(type)) {
            event.setCancelled(true);
        }
    }
}
