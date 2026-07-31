package nl.hauntedmc.serverfeatures.features.nametags.internal;

import org.bukkit.scheduler.BukkitTask;

/**
 * Per-viewer lifecycle state for one client-side nametag entity.
 *
 * <p>The generation token fences delayed work: every hide, rebuild, quit, untrack, or replacement
 * increments it, so a previously scheduled spawn can no longer become visible afterwards.</p>
 */
public final class NametagViewerState {
    private long generation;
    private boolean spawned;
    private BukkitTask pendingSpawn;

    public long nextGeneration() {
        return ++generation;
    }

    public boolean isCurrent(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void markSpawned() {
        this.spawned = true;
    }

    public void markHidden() {
        this.spawned = false;
    }

    public boolean hasPendingSpawn() {
        return pendingSpawn != null;
    }

    public BukkitTask replacePendingSpawn(BukkitTask replacement) {
        BukkitTask previous = pendingSpawn;
        pendingSpawn = replacement;
        return previous;
    }

    public BukkitTask clearPendingSpawn() {
        BukkitTask previous = pendingSpawn;
        pendingSpawn = null;
        return previous;
    }
}
