package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread registry with consistent indexes by entity, source spawner and last known entity chunk.
 */
public final class SpawnerMobRegistry {

    private final Map<UUID, TrackedSpawnerMob> byEntity = new HashMap<>();
    private final Map<SpawnerKey, Set<UUID>> bySpawner = new HashMap<>();
    private final Map<EntityChunkKey, Set<UUID>> byEntityChunk = new HashMap<>();

    public void load(Collection<TrackedSpawnerMob> records) {
        clear();
        if (records == null) {
            return;
        }
        for (TrackedSpawnerMob record : records) {
            if (record != null) {
                put(record);
            }
        }
    }

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

    public int size() {
        return byEntity.size();
    }

    public TrackedSpawnerMob put(TrackedSpawnerMob record) {
        TrackedSpawnerMob previous = byEntity.put(record.entityId(), record);
        if (previous != null) {
            removeFromIndex(bySpawner, previous.spawner(), previous.entityId());
            removeFromIndex(byEntityChunk, previous.entityChunk(), previous.entityId());
        }

        bySpawner.computeIfAbsent(record.spawner(), ignored -> new LinkedHashSet<>())
                .add(record.entityId());
        byEntityChunk.computeIfAbsent(record.entityChunk(), ignored -> new LinkedHashSet<>())
                .add(record.entityId());
        return previous;
    }

    public Optional<TrackedSpawnerMob> remove(UUID entityId) {
        TrackedSpawnerMob removed = byEntity.remove(entityId);
        if (removed == null) {
            return Optional.empty();
        }

        removeFromIndex(bySpawner, removed.spawner(), entityId);
        removeFromIndex(byEntityChunk, removed.entityChunk(), entityId);
        return Optional.of(removed);
    }

    public Set<UUID> entityIdsInChunk(EntityChunkKey chunk) {
        Set<UUID> entities = byEntityChunk.get(chunk);
        return entities == null ? Set.of() : Set.copyOf(entities);
    }

    public List<TrackedSpawnerMob> snapshot() {
        List<TrackedSpawnerMob> records = new ArrayList<>(byEntity.values());
        records.sort(Comparator.comparing(record -> record.entityId().toString()));
        return List.copyOf(records);
    }

    public void clear() {
        byEntity.clear();
        bySpawner.clear();
        byEntityChunk.clear();
    }

    private static <K> void removeFromIndex(Map<K, Set<UUID>> index, K key, UUID entityId) {
        Set<UUID> entities = index.get(key);
        if (entities == null) {
            return;
        }
        entities.remove(entityId);
        if (entities.isEmpty()) {
            index.remove(key);
        }
    }
}
