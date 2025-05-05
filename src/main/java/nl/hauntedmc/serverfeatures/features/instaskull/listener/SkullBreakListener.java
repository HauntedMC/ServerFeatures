package nl.hauntedmc.serverfeatures.features.instaskull.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;

public class SkullBreakListener implements Listener {

    public SkullBreakListener() {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("serverfeatures.feature.instaskull.use")) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD) {
            event.setInstaBreak(true);
        }
    }
}