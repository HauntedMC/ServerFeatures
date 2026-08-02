package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class LimitSpawnersListener implements Listener {

    private final LimitSpawners feature;
    private final LimitSpawnersHandler handler;

    public LimitSpawnersListener(LimitSpawners feature, LimitSpawnersHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        BlockState spawner = event.getSpawner();
        if (spawner == null || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        Location location = spawner.getLocation();
        if (!handler.tryRegisterSpawn(living, SpawnerKey.of(location))) {
            event.setCancelled(true);
        }
    }

    /**
     * Final validation is deferred one tick so cancellation by a later MONITOR listener is visible.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnerSpawnFinalState(SpawnerSpawnEvent event) {
        handler.scheduleSpawnFinalization(event.getEntity(), event::isCancelled);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        handler.unregisterIfTracked(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        handler.scheduleRemovalCheck(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        Location destination = event.getTo();
        if (destination != null) {
            handler.updateTrackedLocation(event.getEntity(), destination);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (chunk.isLoaded()) {
                handler.handleChunkLoad(chunk);
            }
        }, BukkitTime.ticks(1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        handler.handleChunkUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        handler.handleWorldUnload(event.getWorld());
    }
}
