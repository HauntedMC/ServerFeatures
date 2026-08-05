package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankChunkKey;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankPosition;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class TankIndex {

    private final Map<TankPosition, AbstractTank> byPosition = new LinkedHashMap<>();
    private final Map<TankChunkKey, Set<AbstractTank>> byChunk = new HashMap<>();

    AbstractTank put(TankPosition position, AbstractTank tank) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(tank, "tank");
        AbstractTank previous = byPosition.put(position, tank);
        if (previous != null) {
            removeFromChunk(position.chunkKey(), previous);
        }
        byChunk.computeIfAbsent(position.chunkKey(), ignored -> new LinkedHashSet<>()).add(tank);
        return previous;
    }

    AbstractTank get(TankPosition position) {
        return byPosition.get(position);
    }

    boolean remove(TankPosition position, AbstractTank expected) {
        if (!byPosition.remove(position, expected)) {
            return false;
        }
        removeFromChunk(position.chunkKey(), expected);
        return true;
    }

    int count(TankChunkKey chunkKey) {
        Set<AbstractTank> tanks = byChunk.get(chunkKey);
        return tanks == null ? 0 : tanks.size();
    }

    boolean hasTanks(TankChunkKey chunkKey) {
        Set<AbstractTank> tanks = byChunk.get(chunkKey);
        return tanks != null && !tanks.isEmpty();
    }

    Set<AbstractTank> tanks(TankChunkKey chunkKey) {
        Set<AbstractTank> tanks = byChunk.get(chunkKey);
        return tanks == null ? Set.of() : Set.copyOf(tanks);
    }

    List<AbstractTank> nearby(UUID worldId, int chunkX, int chunkZ, int radiusChunks) {
        List<AbstractTank> result = new ArrayList<>();
        for (int x = chunkX - radiusChunks; x <= chunkX + radiusChunks; x++) {
            for (int z = chunkZ - radiusChunks; z <= chunkZ + radiusChunks; z++) {
                Collection<AbstractTank> tanks = byChunk.get(new TankChunkKey(worldId, x, z));
                if (tanks != null) {
                    result.addAll(tanks);
                }
            }
        }
        return result;
    }

    List<AbstractTank> snapshot() {
        return List.copyOf(byPosition.values());
    }

    void clear() {
        byPosition.clear();
        byChunk.clear();
    }

    private void removeFromChunk(TankChunkKey chunkKey, AbstractTank tank) {
        Set<AbstractTank> tanks = byChunk.get(chunkKey);
        if (tanks == null) {
            return;
        }
        tanks.remove(tank);
        if (tanks.isEmpty()) {
            byChunk.remove(chunkKey);
        }
    }
}
