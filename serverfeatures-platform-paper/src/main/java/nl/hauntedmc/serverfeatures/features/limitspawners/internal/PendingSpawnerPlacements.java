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

    public static void cancel(SpawnerKey position) {
        POSITIONS.computeIfPresent(position, (ignored, state) -> State.CANCELLED);
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
     * <p>Committed positions may be indexed, but remain reserved until the placement finalizer has
     * completed so a second placement cannot overwrite the first reservation.</p>
     */
    public static boolean blocksIndexing(SpawnerKey position) {
        State state = POSITIONS.get(position);
        return state == State.PROVISIONAL || state == State.CANCELLED;
    }

    public static void clearAll() {
        POSITIONS.clear();
    }

    private enum State {
        PROVISIONAL,
        COMMITTED,
        CANCELLED
    }
}
