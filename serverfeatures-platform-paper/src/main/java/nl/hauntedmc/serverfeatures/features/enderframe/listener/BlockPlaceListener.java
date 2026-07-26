package nl.hauntedmc.serverfeatures.features.enderframe.listener;

import nl.hauntedmc.serverfeatures.features.enderframe.EnderFrame;
import nl.hauntedmc.serverfeatures.features.enderframe.util.LocationUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Prevent placing END_PORTAL_FRAME inside a Stronghold. Also respects WorldGuard/GriefPrevention.
 */
public class BlockPlaceListener implements Listener {

    private final EnderFrame feature;

    public BlockPlaceListener(EnderFrame feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("serverfeatures.feature.enderframe.use")) {
            event.setCancelled(true);
            return;
        }

        if (LocationUtils.isInStronghold(block)) {
            event.setCancelled(true);
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("enderframe.stronghold_place_restricted")
                    .forAudience(player)
                    .build());
        }

    }

}
