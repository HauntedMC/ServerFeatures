package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-thread-only guard preventing chunk reconciliation from committing provisional block places.
 */
public final class PendingSpawnerPlacements {

    private static final Set<SpawnerKey> POSITIONS = new HashSet<>();

    private PendingSpawnerPlacements() {
    }

    public static void mark(SpawnerKey position) {
        POSITIONS.add(position);
    }

    public static void clear(SpawnerKey position) {
        POSITIONS.remove(position);
    }

    public static boolean contains(SpawnerKey position) {
        return POSITIONS.contains(position);
    }

    public static void clearAll() {
        POSITIONS.clear();
    }
}
