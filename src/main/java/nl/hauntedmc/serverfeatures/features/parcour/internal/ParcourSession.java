package nl.hauntedmc.serverfeatures.features.parcour.internal;

import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourDefinition;
import nl.hauntedmc.serverfeatures.features.parcour.model.ParcourRegion;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ParcourSession {
    public final UUID playerId;
    public final String parcourId;

    private long startMillis;

    private int expectedNextOrder;
    private Location restoreLocation;

    private final Set<Integer> triggeredOrders = new HashSet<>();

    private BukkitTask actionBarTask;
    private BukkitTask particleTask;

    private boolean finished;
    private long finalElapsedMs;
    private ParcourInventorySnapshot snapshot;
    private long lastCheckpointTeleportMs = 0L;

    private boolean countdownActive;
    private BukkitTask countdownTask;
    private Location frozenAt;

    private PotionEffectType appliedEffectType;
    private PotionEffect previousEffect;

    public ParcourSession(UUID playerId, ParcourDefinition def, Location startRestore, int firstExpectedOrder) {
        this.playerId = playerId;
        this.parcourId = def.id();
        this.startMillis = System.currentTimeMillis();
        this.restoreLocation = startRestore;
        this.expectedNextOrder = firstExpectedOrder;
        this.finished = false;
        this.finalElapsedMs = 0L;
        this.countdownActive = false;
    }

    public long startMillis() {
        return startMillis;
    }

    public void setStartToNow() {
        this.startMillis = System.currentTimeMillis();
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

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public void setCountdownActive(boolean active) {
        this.countdownActive = active;
    }

    public void setCountdownTask(BukkitTask task) {
        this.countdownTask = task;
    }

    public void cancelCountdownTask() {
        if (this.countdownTask != null) {
            try {
                this.countdownTask.cancel();
            } catch (Throwable ignored) {
            }
            this.countdownTask = null;
        }
    }

    public Location frozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(Location frozenAt) {
        this.frozenAt = frozenAt;
    }

    public PotionEffectType appliedEffectType() {
        return appliedEffectType;
    }

    public void setAppliedEffectType(PotionEffectType type) {
        this.appliedEffectType = type;
    }

    public PotionEffect previousEffect() {
        return previousEffect;
    }

    public void setPreviousEffect(PotionEffect previousEffect) {
        this.previousEffect = previousEffect;
    }
}
