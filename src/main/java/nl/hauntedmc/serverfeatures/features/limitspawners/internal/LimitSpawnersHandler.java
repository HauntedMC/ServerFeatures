package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight per-spawner cap enforcement without PDC persistence.
 * - buckets: SpawnerKey -> set of alive entity UUIDs from that spawner
 * - reverse: entity UUID -> SpawnerKey (for O(1) unregister/transfer)
 * Scope: runtime-only; we don't persist across unloads/restarts.
 */
public final class LimitSpawnersHandler {

    private final int maxSpawn;
    private final boolean removeOnChunkUnload;

    /** Per-spawner set of currently alive entity UUIDs (best-effort, pruned lazily). */
    private final Map<SpawnerKey, Set<UUID>> buckets = new ConcurrentHashMap<>();
    /** Reverse index for O(1) lookups on death/remove/transform. */
    private final Map<UUID, SpawnerKey> reverse = new ConcurrentHashMap<>();

    public LimitSpawnersHandler(LimitSpawners feature) {
        var cfg = feature.getConfigHandler();
        this.maxSpawn = Math.max(0, cfg.node("max_spawn").as(Integer.class, 4));
        this.removeOnChunkUnload = cfg.node("remove_mobs_on_chunk_unload").as(Boolean.class, true);
    }

    /**
     * Attempt to register a newly spawned entity for a specific spawner.
     * @return true if allowed, false if the spawner is at/over the cap.
     */
    public boolean tryRegisterSpawn(LivingEntity e, SpawnerKey key) {
        Set<UUID> ids = getBucket(key);
        prune(ids);
        if (ids.size() >= maxSpawn) return false;

        UUID id = e.getUniqueId();
        ids.add(id);
        reverse.put(id, key);

        if (removeOnChunkUnload) {
            // Hint to the server that these should be culled when far; explicit unload cleanup still runs.
            e.setRemoveWhenFarAway(true);
        }
        return true;
    }

    /**
     * Unregister an entity if it was tracked (death, despawn, plugin removal, etc).
     */
    public void unregisterIfTracked(Entity e) {
        UUID id = e.getUniqueId();
        SpawnerKey key = reverse.remove(id);
        if (key == null) return;

        Set<UUID> ids = buckets.get(key);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) buckets.remove(key);
        }
    }

    /**
     * Transfer tracking from an original entity to its transformed replacement (e.g., villager -> zombie villager).
     * No cap check: this replaces an existing tracked entity 1:1.
     */
    public void transferTracking(Entity original, LivingEntity transformed) {
        UUID oldId = original.getUniqueId();
        SpawnerKey key = reverse.get(oldId);
        if (key == null) return; // not tracked by us

        // Atomic-ish swap in our maps without a cap check.
        Set<UUID> ids = getBucket(key);
        UUID newId = transformed.getUniqueId();

        ids.add(newId);
        reverse.put(newId, key);

        // keep Paper's despawn hint consistent with config
        if (removeOnChunkUnload) {
            transformed.setRemoveWhenFarAway(true);
        }

        // Now drop the old mapping
        reverse.remove(oldId);
        ids.remove(oldId);
        if (ids.isEmpty()) {
            buckets.remove(key);
        }
    }

    /**
     * Current alive count for a spawner (after pruning stale UUIDs).
     */
    public int currentAliveCount(SpawnerKey key) {
        Set<UUID> ids = buckets.get(key);
        if (ids == null || ids.isEmpty()) return 0;
        prune(ids);
        return ids.size();
    }

    /**
     * On chunk unload:
     * - If configured, remove all tracked spawner mobs in this chunk (hard remove).
     * - Always drop bucket entries whose spawner position lies in this chunk.
     */
    public void handleChunkUnload(Chunk chunk) {
        if (removeOnChunkUnload) {
            for (Entity ent : chunk.getEntities()) {
                if (ent instanceof LivingEntity) {
                    UUID id = ent.getUniqueId();
                    if (reverse.containsKey(id)) {
                        reverse.remove(id);
                        ent.remove();
                    }
                }
            }
        }

        UUID worldId = chunk.getWorld().getUID();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        buckets.keySet().removeIf(key ->
                key.worldId().equals(worldId)
                        && key.x() >= baseX && key.x() < (baseX + 16)
                        && key.z() >= baseZ && key.z() < (baseZ + 16)
        );
    }

    /**
     * Optional hardening: drop all state for a world when it unloads.
     */
    public void dropWorld(UUID worldId) {
        buckets.keySet().removeIf(k -> k.worldId().equals(worldId));
        reverse.entrySet().removeIf(e -> e.getValue().worldId().equals(worldId));
    }

    public Optional<World> resolveWorld(UUID worldId) {
        return Optional.ofNullable(Bukkit.getWorld(worldId));
    }

    private Set<UUID> getBucket(SpawnerKey key) {
        return buckets.computeIfAbsent(key, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    }

    /**
     * Remove dead/invalid UUIDs from a bucket (cheap, bounded by maxSpawn).
     */
    private void prune(Set<UUID> ids) {
        if (ids.isEmpty()) return;
        ids.removeIf(uuid -> {
            Entity ent = Bukkit.getEntity(uuid);
            return !(ent instanceof LivingEntity le) || le.isDead() || !le.isValid();
        });
    }
}
