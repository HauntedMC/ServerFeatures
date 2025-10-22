package nl.hauntedmc.serverfeatures.features.enderframe.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import io.papermc.paper.math.Position;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.StructureKeys;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import nl.hauntedmc.serverfeatures.features.enderframe.EnderFrame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.generator.structure.Structure;
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

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {

        Block block = event.getBlock();
        if (block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.hasPermission("serverfeatures.feature.enderframe.use")) {
            return;
        }

        // --- Disallow breaking frames inside a natural Stronghold (Paper) ---
        if (isInStronghold(block)) {
            event.setCancelled(true);
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("enderframe.stronghold_restricted")
                    .forAudience(player)
                    .build());
            return;
        }

        // --- WorldGuard region check (if present/enabled) ---
        if (feature.isWorldguardEnabled()) {
            if (isWorldGuardBreakDenied(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("enderframe.worldguard_restricted")
                        .forAudience(player)
                        .build());
                return;
            }
        }

        // --- GriefPrevention check, only if GP is loaded/active ---
        if (feature.isGriefPreventionEnabled()) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(block.getLocation(), false, null);
            if (claim != null && claim.allowBreak(player, block.getType()) != null) {
                // The player doesn't have trust/permission in this claim
                event.setCancelled(true);
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("enderframe.claim_restricted")
                        .forAudience(player)
                        .build());
                return;
            }
        }

        // Instantly break the END_PORTAL_FRAME:
        event.setInstaBreak(true);
        Location dropLocation = block.getLocation().clone().add(0, 1, 0);
        block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.END_PORTAL_FRAME));

        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("enderframe.pickup_success")
                .forAudience(player)
                .build());

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

    /**
     * Paper-native structure containment check.
     * Returns true if the block is inside a generated Stronghold in the Overworld.
     */
    private boolean isInStronghold(Block block) {
        World world = block.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false; // Strongholds only generate in the Overworld
        }

        // Fetch the stronghold structure from the Paper registry
        Structure stronghold = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.STRUCTURE)
                .get(StructureKeys.STRONGHOLD);

        if (stronghold == null) {
            return false;
        }

        // Does this exact position lie within a generated Stronghold?
        return world.hasStructureAt(Position.block(block.getLocation()), stronghold);
    }

    /**
     * WorldGuard: return true if breaking is denied at this location for this player.
     * Safe to call even if WorldGuard is not installed (returns false).
     */
    private boolean isWorldGuardBreakDenied(Player player, Location loc) {
        WorldGuardPlugin wg = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null) return false;

        LocalPlayer lp = wg.wrapPlayer(player);
        com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(loc);

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        boolean canBreak = query.testBuild(weLoc, lp);

        return !canBreak;
    }
}
