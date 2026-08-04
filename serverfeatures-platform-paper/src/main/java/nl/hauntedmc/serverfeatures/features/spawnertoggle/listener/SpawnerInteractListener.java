package nl.hauntedmc.serverfeatures.features.spawnertoggle.listener;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.spawnertoggle.SpawnerToggle;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SpawnerInteractListener implements Listener {

    private final SpawnerToggle feature;

    public SpawnerInteractListener(SpawnerToggle feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        int x = clicked.getX();
        int y = clicked.getY();
        int z = clicked.getZ();
        var world = clicked.getWorld();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (event.useInteractedBlock() == Event.Result.DENY) {
                return;
            }
            Block current = world.getBlockAt(x, y, z);
            if (current.getType() != Material.SPAWNER) {
                return;
            }
            if (!feature.mayToggle(player)) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("general.no_permission")
                        .forAudience(player)
                        .build());
                return;
            }
            if (feature.isGriefPreventionEnabled()
                    && !feature.checkBuildPermissions(player, current.getLocation())) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("spawner_toggle.claim_restricted")
                        .forAudience(player)
                        .build());
                return;
            }
            feature.toggleSpawner(player, current);
        }, BukkitTime.ticks(1));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getChunk();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> feature.refreshChunkVisuals(player, chunk),
                BukkitTime.ticks(1)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        if (spawner != null && feature.isDisabled(spawner)) {
            event.setCancelled(true);
        }
    }
}
