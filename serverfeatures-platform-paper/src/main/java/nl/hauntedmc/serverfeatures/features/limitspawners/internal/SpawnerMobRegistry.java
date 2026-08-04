package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread-only registry with constant-time entity/source/world lookups and bounded area queries.
 */
public final class SpawnerMobRegistry {

    private final Map<UUID, TrackedSpawnerMob> byEntity = new HashMap<>();
    private final Map<SpawnerKey, Set<UUID>> bySpawner = new HashMap<>();
    private final Map<EntityChunkKey, Set<UUID>> byMobChunk = new HashMap<>();
    private final Map<EntityChunkKey, Set<SpawnerKey>> bySourceChunk = new HashMap<>();
    private final Map<UUID, Integer> worldCounts = new HashMap<>();

    public Optional<TrackedSpawnerMob> get(UUID entityId) {
        return Optional.ofNullable(byEntity.get(entityId));
    }

    public boolean contains(UUID entityId) {
        return byEntity.containsKey(entityId);
    }

    public int count(SpawnerKey spawner) {
        Set<UUID> entities = bySpawner.get(spawner);
        return entities == null ? 0 : entities.size();
    }

    public int countInArea(SpawnerKey center, int radius) {
        int chunkRadius = Math.max(1, (radius + 15) >> 4);
        long radiusSquared = (long) radius * radius;
        int count = 0;
        for (int chunkX = center.chunkX() - chunkRadius;
                chunkX <= center.chunkX() + chunkRadius;
                chunkX++) {
            for (int chunkZ = center.chunkZ() - chunkRadius;
                    chunkZ <= center.chunkZ() + chunkRadius;
                    chunkZ++) {
                Set<SpawnerKey> sources = bySourceChunk.get(
                        new EntityChunkKey(center.worldId(), chunkX, chunkZ)
                );
                if (sources == null) {
                    continue;
                }
                for (SpawnerKey source : sources) {
                    if (center.distanceSquared(source) <= radiusSquared) {
                        count += count(source);
                    }
                }
            }
        }
        return count;
    }

    public int worldCount(UUID worldId) {
        return worldCounts.getOrDefault(worldId, 0);
    }

    public int size() {
        return byEntity.size();
    }

    public TrackedSpawnerMob put(TrackedSpawnerMob record) {
        TrackedSpawnerMob previous = byEntity.put(record.entityId(), record);
        if (previous != null) {
            removeIndexes(previous);
        }
        addIndexes(record);
        return previous;
    }

    public Optional<TrackedSpawnerMob> remove(UUID entityId) {
        TrackedSpawnerMob removed = byEntity.remove(entityId);
        if (removed == null) {
            return Optional.empty();
        }
        removeIndexes(removed);
        return Optional.of(removed);
    }

    public Set<UUID> entityIdsForSpawner(SpawnerKey spawner) {
        Set<UUID> entities = bySpawner.get(spawner);
        return entities == null ? Set.of() : Set.copyOf(entities);
    }

    public Set<UUID> entityIdsInMobChunk(EntityChunkKey chunk) {
        Set<UUID> entities = byMobChunk.get(chunk);
        return entities == null ? Set.of() : Set.copyOf(entities);
    }

    public Set<SpawnerKey> spawnersInSourceChunk(EntityChunkKey chunk) {
        Set<SpawnerKey> spawners = bySourceChunk.get(chunk);
        return spawners == null ? Set.of() : Set.copyOf(spawners);
    }

    public Set<SpawnerKey> sources() {
        return Set.copyOf(bySpawner.keySet());
    }

    public List<TrackedSpawnerMob> snapshot() {
        List<TrackedSpawnerMob> records = new ArrayList<>(byEntity.values());
        records.sort(Comparator.comparing(record -> record.entityId().toString()));
        return List.copyOf(records);
    }

    public void clear() {
        byEntity.clear();
        bySpawner.clear();
        byMobChunk.clear();
        bySourceChunk.clear();
        worldCounts.clear();
    }

    private void addIndexes(TrackedSpawnerMob record) {
        bySpawner.computeIfAbsent(record.spawner(), ignored -> new LinkedHashSet<>())
                .add(record.entityId());
        byMobChunk.computeIfAbsent(record.entityChunk(), ignored -> new LinkedHashSet<>())
                .add(record.entityId());
        bySourceChunk.computeIfAbsent(record.spawner().chunkKey(), ignored -> new LinkedHashSet<>())
                .add(record.spawner());
        worldCounts.merge(record.spawner().worldId(), 1, Integer::sum);
    }

    private void removeIndexes(TrackedSpawnerMob record) {
        removeFromIndex(bySpawner, record.spawner(), record.entityId());
        removeFromIndex(byMobChunk, record.entityChunk(), record.entityId());

        if (!bySpawner.containsKey(record.spawner())) {
            removeFromIndex(bySourceChunk, record.spawner().chunkKey(), record.spawner());
        }

        worldCounts.computeIfPresent(record.spawner().worldId(), (ignored, current) -> {
            int next = current - 1;
            return next <= 0 ? null : next;
        });
    }

    private static <K, V> void removeFromIndex(Map<K, Set<V>> index, K key, V value) {
        Set<V> values = index.get(key);
        if (values == null) {
            return;
        }
        values.remove(value);
        if (values.isEmpty()) {
            index.remove(key);
        }
    }
}
