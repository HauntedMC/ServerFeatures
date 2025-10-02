package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public final class LimitSpawnersListener implements Listener {

    private final LimitSpawnersHandler handler;

    public LimitSpawnersListener(LimitSpawnersHandler handler) {
        this.handler = handler;
    }

    /* ===== Primary (Paper/Bukkit) spawner spawn hook ===== */

    /**
     * Prefer SpawnerSpawnEvent: fires for actual block spawners and exposes the spawner block state.
     * We can cancel here very cheaply before the entity is fully “in play”.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        BlockState state = event.getSpawner();
        if (state == null) return;

        LivingEntity ent = (LivingEntity) event.getEntity();

        Location loc = state.getLocation();
        SpawnerKey key = SpawnerKey.of(loc);

        // Try to register; cancel if over limit
        if (!handler.tryRegisterSpawn(ent, key)) {
            event.setCancelled(true);
        }
    }

    /* ===== Clean up when entities die/are removed ===== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        handler.unregisterIfTracked(event.getEntity());
    }

    // Paper event: catches removals that are not “death” (e.g., despawn, unload, plugin remove)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        handler.unregisterIfTracked(event.getEntity());
    }

    /* ===== Chunk lifecycle ===== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk c = event.getChunk();
        handler.indexChunk(c);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        handler.handleChunkUnload(event.getChunk());
    }

}
