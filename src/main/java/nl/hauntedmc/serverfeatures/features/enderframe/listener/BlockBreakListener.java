package nl.hauntedmc.serverfeatures.features.enderframe.listener;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import nl.hauntedmc.serverfeatures.features.enderframe.EnderFrame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The event listener that handles the actual block break logic.
 * We use BlockDamageEvent so we can insta-break the frame, similarly to InstaSkull.
 */
public class BlockBreakListener implements Listener {

    private final EnderFrame feature;

    public BlockBreakListener(EnderFrame feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("serverfeatures.feature.enderframe.use")) {
            return;
        }

        // --- GriefPrevention check, only if GP is loaded/active ---
        if (feature.isGriefPreventionEnabled()) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(block.getLocation(), false, null);
            if (claim != null && claim.allowBreak(player, block.getType()) != null) {
                // The player doesn't have trust/permission in this claim
                event.setCancelled(true);
                player.sendMessage(feature.getLocalizationHandler().getMessage("enderframe.claim_restricted"));
                return;
            }
        }

        // Instantly break the END_PORTAL_FRAME:
        event.setInstaBreak(true);
        Location dropLocation = block.getLocation().clone().add(0, 1, 0);
        block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.END_PORTAL_FRAME));

        player.sendMessage(feature.getLocalizationHandler().getMessage("enderframe.pickup_success"));

        // Remove any END_PORTAL blocks within a certain radius (on the same Y-level):
        int radius = (int) feature.getConfigHandler().getSetting("pickup_radius");
        Location location = block.getLocation();
        for (int x = location.getBlockX() - radius; x <= location.getBlockX() + radius; x++) {
            for (int z = location.getBlockZ() - radius; z <= location.getBlockZ() + radius; z++) {
                Block foundBlock = location.getWorld().getBlockAt(x, location.getBlockY(), z);
                if (foundBlock.getType() == Material.END_PORTAL) {
                    foundBlock.setType(Material.AIR);
                }
            }
        }
    }
}