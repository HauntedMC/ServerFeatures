package nl.hauntedmc.serverfeatures.features.parcour.internal;

import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ParcourSession {
    public final UUID playerId;
    public final String parcourId;
    public final long startMillis;

    // Expected next checkpoint order (0,1,2,...) — when no checkpoint with this order exists, END is expected.
    private int expectedNextOrder;

    // Last restore/respawn location
    private Location restoreLocation;

    // Regions triggered in this session (avoid re-running commands on the same region)
    private final Set<Integer> triggeredOrders = new HashSet<>();

    // Actionbar task handle (nullable)
    private BukkitTask actionBarTask;

    private BukkitTask particleTask;

    // Finished state: freeze timer and show N/N for a short hold
    private boolean finished;
    private long finalElapsedMs;
    private ParcourInventorySnapshot snapshot;
    private long lastCheckpointTeleportMs = 0L;

    public ParcourSession(UUID playerId, ParcourDefinition def, Location startRestore, int firstExpectedOrder) {
        this.playerId = playerId;
        this.parcourId = def.id();
        this.startMillis = System.currentTimeMillis();
        this.restoreLocation = startRestore;
        this.expectedNextOrder = firstExpectedOrder;
        this.finished = false;
        this.finalElapsedMs = 0L;
    }

    public int expectedNextOrder() {
        return expectedNextOrder;
    }

    public void advanceExpectedOrder() {
        expectedNextOrder++;
    }

    public void setExpectedOrder(int ord) {
        expectedNextOrder = ord;
    }

    public Location restoreLocation() {
        return restoreLocation;
    }

    public void setRestoreLocation(Location loc) {
        this.restoreLocation = loc;
    }

    public boolean markTriggered(ParcourRegion region) {
        return triggeredOrders.add(region.order());
    }

    public boolean alreadyTriggered(ParcourRegion region) {
        return triggeredOrders.contains(region.order());
    }

    public void setActionBarTask(BukkitTask task) {
        this.actionBarTask = task;
    }

    public void cancelActionBarTask() {
        if (this.actionBarTask != null) {
            try {
                this.actionBarTask.cancel();
            } catch (Throwable ignored) {
            }
            this.actionBarTask = null;
        }
    }

    public void setParticleTask(BukkitTask task) {
        this.particleTask = task;
    }

    public void cancelParticleTask() {
        if (this.particleTask != null) {
            try {
                this.particleTask.cancel();
            } catch (Throwable ignored) {
            }
            this.particleTask = null;
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public void markFinished(long elapsedMs) {
        this.finished = true;
        this.finalElapsedMs = Math.max(0L, elapsedMs);
    }

    public double finalSeconds() {
        return finalElapsedMs / 1000.0;
    }

    public ParcourInventorySnapshot snapshot() {
        return snapshot;
    }

    public void setSnapshot(ParcourInventorySnapshot snap) {
        this.snapshot = snap;
    }

    public long lastCheckpointTeleportMs() {
        return lastCheckpointTeleportMs;
    }

    public void setLastCheckpointTeleportMs(long ms) {
        this.lastCheckpointTeleportMs = ms;
    }
}
