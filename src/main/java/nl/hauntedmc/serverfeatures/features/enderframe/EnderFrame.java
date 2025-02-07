package nl.hauntedmc.serverfeatures.features.enderframe;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.features.enderframe.meta.Meta;
import nl.hauntedmc.serverfeatures.localization.MessageMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class EnderFrame extends BaseFeature<Meta> {

    private boolean griefPreventionEnabled;

    public EnderFrame(ServerFeatures plugin) {
        super(plugin, new Meta());
    }

    /**
     * Provide default settings for this feature.
     */
    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("require_permission", true);
        defaults.put("pickup_radius", 5);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("enderframe.pickup_success", "&aJe hebt een Ender Frame opgepakt!");
        messageMap.add("enderframe.claim_restricted", "&cJe kunt de Ender Frame niet oppakken in andermans claim.");
        return messageMap;
    }

    /**
     * Called when the feature is enabled. Register your listener(s) here.
     */
    @Override
    public void initialize() {
        // Register the internal event listener:
        getLifecycleManager().registerListener(new EnderFrameBreakListener());
        griefPreventionEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
    }

    /**
     * The event listener that handles the actual block break logic.
     * We use BlockDamageEvent so we can insta-break the frame, similarly to InstaSkull.
     */
    private class EnderFrameBreakListener implements Listener {

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

            boolean requiresPermission = getConfigHandler().getBoolean("require_permission", true);
            if (requiresPermission) {
                if (!player.hasPermission("serverfeatures.feature.enderframe.use")) {
                    return;
                }
            }

            // --- GriefPrevention check, only if GP is loaded/active ---
            if (griefPreventionEnabled) {
                Claim claim = GriefPrevention.instance.dataStore.getClaimAt(block.getLocation(), false, null);
                if (claim != null && claim.allowBreak(player, block.getType()) != null) {
                    // The player doesn't have trust/permission in this claim
                    event.setCancelled(true);
                    player.sendMessage(getLocalizationHandler().getMessage("enderframe.claim_restricted"));
                    return;
                }
            }

            // Instantly break the END_PORTAL_FRAME:
            event.setInstaBreak(true);
            Location dropLocation = block.getLocation().clone().add(0, 1, 0);
            block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.END_PORTAL_FRAME));

            player.sendMessage(getLocalizationHandler().getMessage("enderframe.pickup_success"));

            // Remove any END_PORTAL blocks within a certain radius (on the same Y-level):
            int radius = (int) getConfigHandler().getSetting("pickup_radius");
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
}
