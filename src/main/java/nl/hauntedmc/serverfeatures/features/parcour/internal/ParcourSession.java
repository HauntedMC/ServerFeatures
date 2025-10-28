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

    // NEW: actionbar task for live updates
    private BukkitTask actionBarTask;

    public ParcourSession(UUID playerId, ParcourDefinition def, Location startRestore, int firstExpectedOrder) {
        this.playerId = playerId;
        this.parcourId = def.id();
        this.startMillis = System.currentTimeMillis();
        this.restoreLocation = startRestore;
        this.expectedNextOrder = firstExpectedOrder;
    }

    public int expectedNextOrder() { return expectedNextOrder; }
    public void advanceExpectedOrder() { expectedNextOrder++; }
    public void setExpectedOrder(int ord) { expectedNextOrder = ord; }

    public Location restoreLocation() { return restoreLocation; }
    public void setRestoreLocation(Location loc) { this.restoreLocation = loc; }

    public boolean markTriggered(ParcourRegion region) {
        return triggeredOrders.add(region.order());
    }

    public boolean alreadyTriggered(ParcourRegion region) {
        return triggeredOrders.contains(region.order());
    }

    // NEW: actionbar task handling
    public void setActionBarTask(BukkitTask task) { this.actionBarTask = task; }
    public void cancelActionBarTask() {
        if (actionBarTask != null) {
            try { actionBarTask.cancel(); } catch (Throwable ignored) {}
            actionBarTask = null;
        }
    }
}
