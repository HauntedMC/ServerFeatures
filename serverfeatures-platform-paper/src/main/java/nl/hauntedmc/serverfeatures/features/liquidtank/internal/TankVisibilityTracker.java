package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TankVisibilityTracker {

    private static final Delta NO_CHANGE = new Delta(Set.of(), Set.of());

    private final Map<UUID, Set<AbstractTank>> visibleByPlayer = new HashMap<>();

    Delta update(UUID playerId, Set<AbstractTank> desired) {
        Set<AbstractTank> previous = visibleByPlayer.getOrDefault(playerId, Set.of());
        if (previous.equals(desired)) {
            return NO_CHANGE;
        }
        Set<AbstractTank> added = new HashSet<>(desired);
        added.removeAll(previous);
        Set<AbstractTank> removed = new HashSet<>(previous);
        removed.removeAll(desired);

        if (desired.isEmpty()) {
            visibleByPlayer.remove(playerId);
        } else {
            visibleByPlayer.put(playerId, new HashSet<>(desired));
        }
        return new Delta(added, removed);
    }

    Set<AbstractTank> removePlayer(UUID playerId) {
        Set<AbstractTank> removed = visibleByPlayer.remove(playerId);
        return removed == null ? Set.of() : Set.copyOf(removed);
    }

    void forget(UUID playerId, Set<AbstractTank> tanksToForget) {
        Set<AbstractTank> tracked = visibleByPlayer.get(playerId);
        if (tracked == null) {
            return;
        }
        tracked.removeAll(tanksToForget);
        if (tracked.isEmpty()) {
            visibleByPlayer.remove(playerId);
        }
    }

    void removeTank(AbstractTank tank) {
        visibleByPlayer.values().removeIf(tanks -> {
            tanks.remove(tank);
            return tanks.isEmpty();
        });
    }

    void clear() {
        visibleByPlayer.clear();
    }

    record Delta(Set<AbstractTank> added, Set<AbstractTank> removed) {
    }
}
