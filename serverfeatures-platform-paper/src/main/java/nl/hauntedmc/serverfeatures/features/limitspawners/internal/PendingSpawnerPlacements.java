package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-thread-only guard preventing chunk reconciliation from committing provisional block places.
 */
public final class PendingSpawnerPlacements {

    private static final Map<SpawnerKey, State> POSITIONS = new HashMap<>();

    private PendingSpawnerPlacements() {
    }

    public static void mark(SpawnerKey position) {
        POSITIONS.put(position, State.PROVISIONAL);
    }

    public static void commit(SpawnerKey position) {
        POSITIONS.computeIfPresent(position, (ignored, state) -> State.COMMITTED);
    }

    public static void clear(SpawnerKey position) {
        POSITIONS.remove(position);
    }

    public static boolean contains(SpawnerKey position) {
        return POSITIONS.containsKey(position);
    }

    /**
     * Returns whether reconciliation must ignore this position.
     *
     * <p>A committed position is consumed here and may be indexed exactly once by either the
     * placement finalizer or an intervening chunk reconciliation.</p>
     */
    public static boolean blocksIndexing(SpawnerKey position) {
        State state = POSITIONS.get(position);
        if (state == null) {
            return false;
        }
        if (state == State.PROVISIONAL) {
            return true;
        }
        POSITIONS.remove(position);
        return false;
    }

    public static void clearAll() {
        POSITIONS.clear();
    }

    private enum State {
        PROVISIONAL,
        COMMITTED
    }
}
