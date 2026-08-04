package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraveSpatialIndex {
    private final Map<UUID, Map<Long, Set<UUID>>> byWorld = new HashMap<>();
    private final Map<UUID, IndexedPosition> positions = new HashMap<>();

    public synchronized void put(Grave grave) {
        remove(grave.graveId());
        UUID worldId = grave.location().worldUuid();
        long chunkKey = chunkKey(grave.location().chunkX(), grave.location().chunkZ());
        byWorld.computeIfAbsent(worldId, ignored -> new HashMap<>())
                .computeIfAbsent(chunkKey, ignored -> new HashSet<>())
                .add(grave.graveId());
        positions.put(grave.graveId(), new IndexedPosition(worldId, chunkKey));
    }

    public synchronized void remove(UUID graveId) {
        IndexedPosition position = positions.remove(graveId);
        if (position == null) {
            return;
        }
        Map<Long, Set<UUID>> world = byWorld.get(position.worldId());
        if (world == null) {
            return;
        }
        Set<UUID> graves = world.get(position.chunkKey());
        if (graves != null) {
            graves.remove(graveId);
            if (graves.isEmpty()) {
                world.remove(position.chunkKey());
            }
        }
        if (world.isEmpty()) {
            byWorld.remove(position.worldId());
        }
    }

    public synchronized List<UUID> nearby(UUID worldId, int centerChunkX, int centerChunkZ, int radiusChunks) {
        Map<Long, Set<UUID>> world = byWorld.get(worldId);
        if (world == null) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (int x = centerChunkX - radiusChunks; x <= centerChunkX + radiusChunks; x++) {
            for (int z = centerChunkZ - radiusChunks; z <= centerChunkZ + radiusChunks; z++) {
                Set<UUID> graves = world.get(chunkKey(x, z));
                if (graves != null) {
                    result.addAll(graves);
                }
            }
        }
        return List.copyOf(result);
    }

    public synchronized void clear() {
        byWorld.clear();
        positions.clear();
    }

    static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record IndexedPosition(UUID worldId, long chunkKey) {
    }
}
