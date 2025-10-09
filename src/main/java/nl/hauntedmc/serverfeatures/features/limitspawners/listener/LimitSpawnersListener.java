package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
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
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class LimitSpawnersListener implements Listener {

    private final LimitSpawnersHandler handler;
    private final LimitSpawners feature;

    public LimitSpawnersListener(LimitSpawners feature, LimitSpawnersHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        BlockState state = event.getSpawner();
        if (state == null) return;

        LivingEntity ent = (LivingEntity) event.getEntity();
        Location loc = state.getLocation();
        SpawnerKey key = SpawnerKey.of(loc);

        if (!handler.tryRegisterSpawn(ent, key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        handler.unregisterIfTracked(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() ->
            handler.unregisterIfTracked(event.getEntity())
        , BukkitTime.ticks(5));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        handler.handleChunkUnload(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        handler.dropWorld(event.getWorld().getUID());
    }
}
