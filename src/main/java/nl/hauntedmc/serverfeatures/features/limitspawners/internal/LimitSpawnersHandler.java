package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core logic & lightweight tracking.
 * Per-spawner buckets are keyed by (world UUID, x, y, z) of the spawner block location.
 * We mark spawned mobs via PDC so we can rebuild counts on chunk load and clean safely.
 */
public final class LimitSpawnersHandler {

    private static final String PDC_ORIGIN_KEY = "limitspawners_origin"; // String: worldUUID:x:y:y:z

    private final NamespacedKey originKey;

    private final int maxSpawn;
    private final boolean removeOnChunkUnload;

    /** Map of spawner key -> set of tracked entity UUIDs currently alive (best-effort, pruned lazily). */
    private final Map<SpawnerKey, Set<UUID>> buckets = new ConcurrentHashMap<>();

    public LimitSpawnersHandler(LimitSpawners feature) {
        this.originKey = new NamespacedKey(feature.getPlugin(), PDC_ORIGIN_KEY);

        var cfg = feature.getConfigHandler();
        this.maxSpawn = Math.max(0, cfg.node("max_spawn").as(Integer.class, 4));
        this.removeOnChunkUnload = cfg.node("remove_mobs_on_chunk_unload").as(Boolean.class, true);
    }

    public int getMaxSpawn() { return maxSpawn; }
    public boolean isRemoveOnChunkUnload() { return removeOnChunkUnload; }

    /* ===== Tagging helpers ===== */

    public void tagEntityWithOrigin(LivingEntity e, SpawnerKey key) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(originKey, PersistentDataType.STRING, key.toString());
    }

    public Optional<SpawnerKey> readOrigin(Entity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        String raw = pdc.get(originKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return Optional.empty();
        try {
            SpawnerKey k = SpawnerKey.fromString(raw);
            return Optional.ofNullable(k);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public void clearOrigin(Entity e) {
        e.getPersistentDataContainer().remove(originKey);
    }

    /* ===== Bucket management ===== */

    private Set<UUID> getBucket(SpawnerKey key) {
        return buckets.computeIfAbsent(key, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    }

    /** Return current alive count for a spawner, pruning dead/stale entries. */
    public int currentAliveCount(SpawnerKey key) {
        Set<UUID> ids = buckets.get(key);
        if (ids == null || ids.isEmpty()) return 0;

        // prune dead/stale
        ids.removeIf(uuid -> {
            Entity ent = Bukkit.getEntity(uuid);
            return !(ent instanceof LivingEntity le) || le.isDead() || !le.isValid();
        });
        return ids.size();
    }

    /** Try to register a newly spawned entity into the bucket; return true if allowed, false if over limit. */
    public boolean tryRegisterSpawn(LivingEntity e, SpawnerKey key) {
        Set<UUID> ids = getBucket(key);
        // prune first (cheap set-based cleanup)
        if (!ids.isEmpty()) {
            ids.removeIf(uuid -> {
                Entity ent = Bukkit.getEntity(uuid);
                return !(ent instanceof LivingEntity le) || le.isDead() || !le.isValid();
            });
        }
        if (ids.size() >= maxSpawn) return false;

        ids.add(e.getUniqueId());
        tagEntityWithOrigin(e, key);
        // Optional: encourage removal if chunk unload removal is enabled (server may cull more aggressively)
        if (removeOnChunkUnload) {
            e.setRemoveWhenFarAway(true);
        }
        return true;
    }

    /** Called when an entity is removed/dead to keep counts correct. */
    public void unregisterIfTracked(Entity e) {
        readOrigin(e).ifPresent(key -> {
            Set<UUID> ids = buckets.get(key);
            if (ids != null) {
                ids.remove(e.getUniqueId());
                if (ids.isEmpty()) buckets.remove(key);
            }
            clearOrigin(e);
        });
    }

    /* ===== Chunk hooks ===== */

    /** Re-index tracked entities from this chunk into buckets (used on chunk load). */
    public void indexChunk(Chunk chunk) {
        for (Entity ent : chunk.getEntities()) {
            if (!(ent instanceof LivingEntity)) continue;
            Optional<SpawnerKey> key = readOrigin(ent);
            if (key.isEmpty()) continue;
            getBucket(key.get()).add(ent.getUniqueId());
        }
    }

    /**
     * On chunk unload:
     * - If configured, remove all tracked spawner mobs in this chunk (recommended).
     * - Always drop bucket entries for spawners in this chunk to avoid stale refs.
     */
    public void handleChunkUnload(Chunk chunk) {
        if (removeOnChunkUnload) {
            for (Entity ent : chunk.getEntities()) {
                if (!(ent instanceof LivingEntity le)) continue;
                if (readOrigin(le).isPresent()) {
                    le.remove(); // hard remove to ensure no lingering counts
                }
            }
        }
        // Buckets are per-spawner (x,y,z). Remove any bucket whose spawner is in this chunk.
        UUID worldId = chunk.getWorld().getUID();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        buckets.keySet().removeIf(key ->
                key.worldId().equals(worldId)
                        && key.x() >= baseX && key.x() < (baseX + 16)
                        && key.z() >= baseZ && key.z() < (baseZ + 16));
    }

    /* ===== Utility ===== */

    public Optional<World> resolveWorld(UUID worldId) {
        return Optional.ofNullable(Bukkit.getWorld(worldId));
    }
}
