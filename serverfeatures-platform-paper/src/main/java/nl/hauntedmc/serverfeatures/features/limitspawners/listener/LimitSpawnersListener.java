package nl.hauntedmc.serverfeatures.features.limitspawners.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.PendingSpawnerPlacements;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class LimitSpawnersListener implements Listener {

    private final LimitSpawners feature;
    private final LimitSpawnersHandler handler;

    public LimitSpawnersListener(LimitSpawners feature, LimitSpawnersHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) {
            if (handler.config().blockSpawnerMinecarts()) {
                event.setCancelled(true);
                handler.recordSpawnerMinecartBlocked();
            }
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        LimitSpawnersHandler.SpawnDecision decision = handler.tryReserveSpawnerSpawn(
                living,
                spawner
        );
        if (!decision.allowed()) {
            event.setCancelled(true);
            handler.applyBlockedRetry(spawner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnerSpawnFinalState(SpawnerSpawnEvent event) {
        handler.scheduleSpawnFinalization(event.getEntity(), event::isCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSlimeChildSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
            return;
        }
        if (!handler.tryReserveSlimeChild(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlimeChildSpawnFinalState(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
            handler.scheduleSpawnFinalization(event.getEntity(), event::isCancelled);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER || !event.canBuild()) {
            return;
        }

        SpawnerKey position = SpawnerKey.of(event.getBlockPlaced().getLocation());
        if (PendingSpawnerPlacements.contains(position)) {
            event.setCancelled(true);
            return;
        }
        PendingSpawnerPlacements.mark(position);

        LimitSpawnersHandler.PlacementDecision decision = handler.tryReservePlacement(
                event.getPlayer(),
                position
        );
        if (!decision.allowed()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("limitspawners.placement_blocked")
                            .forAudience(event.getPlayer())
                            .with("count", decision.nearbyCount())
                            .with("limit", decision.limit())
                            .with("radius", handler.config().farmRadius())
                            .build()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnerPlaceFinalState(BlockPlaceEvent event) {
        SpawnerKey position = SpawnerKey.of(event.getBlockPlaced().getLocation());
        if (!PendingSpawnerPlacements.contains(position)) {
            return;
        }

        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            boolean cancelled = event.isCancelled();
            if (cancelled) {
                PendingSpawnerPlacements.cancel(position);
            } else {
                PendingSpawnerPlacements.commit(position);
            }
            handler.schedulePlacementFinalization(position, () -> cancelled);
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> PendingSpawnerPlacements.clear(position),
                    BukkitTime.ticks(2)
            );
        }, BukkitTime.ticks(1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.SPAWNER) {
            handler.scheduleSourceValidation(SpawnerKey.of(event.getBlock().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        scheduleDestroyedSpawners(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        scheduleDestroyedSpawners(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        handler.handleEntityDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        String cause = event.getCause().name();
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
                if (Bukkit.getEntity(entity.getUniqueId()) == null) {
                    handler.handleEntityRemoval(entity, cause);
                }
            }, BukkitTime.ticks(1));
            return;
        }
        handler.handleEntityRemoval(entity, cause);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (event.isCancelled()) {
                return;
            }
            Location destination = event.getTo();
            if (destination != null) {
                handler.handleTeleport(entity, destination);
            }
        }, BukkitTime.ticks(1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType() == Material.SPAWNER) {
            handler.scheduleToggleValidation(SpawnerKey.of(clicked.getLocation()));
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        handler.handleEntitiesLoad(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        handler.handleChunkUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        handler.handleWorldUnload(event.getWorld());
    }

    private void scheduleDestroyedSpawners(Iterable<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.SPAWNER) {
                handler.scheduleSourceValidation(SpawnerKey.of(block.getLocation()));
            }
        }
    }
}
