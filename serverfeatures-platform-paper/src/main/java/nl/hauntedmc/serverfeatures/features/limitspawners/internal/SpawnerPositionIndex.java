package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.EntityChunkKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent, low-churn spatial index for actual spawner block positions.
 */
public final class SpawnerPositionIndex {

    private final Set<SpawnerKey> positions = new LinkedHashSet<>();
    private final Map<EntityChunkKey, Set<SpawnerKey>> byChunk = new HashMap<>();

    public void load(Collection<SpawnerKey> stored) {
        clear();
        if (stored == null) {
            return;
        }
        for (SpawnerKey spawner : stored) {
            if (spawner != null) {
                addInternal(spawner);
            }
        }
    }

    public boolean add(SpawnerKey spawner) {
        if (PendingSpawnerPlacements.contains(spawner)) {
            return false;
        }
        return addInternal(spawner);
    }

    public boolean remove(SpawnerKey spawner) {
        if (!positions.remove(spawner)) {
            return false;
        }
        Set<SpawnerKey> chunkPositions = byChunk.get(spawner.chunkKey());
        if (chunkPositions != null) {
            chunkPositions.remove(spawner);
            if (chunkPositions.isEmpty()) {
                byChunk.remove(spawner.chunkKey());
            }
        }
        return true;
    }

    public boolean contains(SpawnerKey spawner) {
        return positions.contains(spawner);
    }

    public int size() {
        return positions.size();
    }

    public int countWithin(SpawnerKey center, int radius) {
        int chunkRadius = Math.max(1, (radius + 15) >> 4);
        long radiusSquared = (long) radius * radius;
        int count = 0;
        for (int chunkX = center.chunkX() - chunkRadius;
                chunkX <= center.chunkX() + chunkRadius;
                chunkX++) {
            for (int chunkZ = center.chunkZ() - chunkRadius;
                    chunkZ <= center.chunkZ() + chunkRadius;
                    chunkZ++) {
                Set<SpawnerKey> chunkPositions = byChunk.get(
                        new EntityChunkKey(center.worldId(), chunkX, chunkZ)
                );
                if (chunkPositions == null) {
                    continue;
                }
                for (SpawnerKey position : chunkPositions) {
                    if (center.distanceSquared(position) <= radiusSquared) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public Set<SpawnerKey> positionsInChunk(UUID worldId, int chunkX, int chunkZ) {
        Set<SpawnerKey> chunkPositions = byChunk.get(new EntityChunkKey(worldId, chunkX, chunkZ));
        return chunkPositions == null ? Set.of() : Set.copyOf(chunkPositions);
    }

    public List<SpawnerKey> snapshot() {
        List<SpawnerKey> snapshot = new ArrayList<>(positions);
        snapshot.sort(Comparator
                .comparing((SpawnerKey key) -> key.worldId().toString())
                .thenComparingInt(SpawnerKey::x)
                .thenComparingInt(SpawnerKey::y)
                .thenComparingInt(SpawnerKey::z));
        return List.copyOf(snapshot);
    }

    public void clear() {
        positions.clear();
        byChunk.clear();
    }

    private boolean addInternal(SpawnerKey spawner) {
        if (!positions.add(spawner)) {
            return false;
        }
        byChunk.computeIfAbsent(spawner.chunkKey(), ignored -> new LinkedHashSet<>()).add(spawner);
        return true;
    }
}
