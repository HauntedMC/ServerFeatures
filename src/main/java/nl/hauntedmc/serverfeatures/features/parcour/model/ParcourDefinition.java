// File: nl/hauntedmc/serverfeatures/features/parcour/model/ParcourDefinition.java
package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public final class ParcourDefinition {
    private final String id;

    private ParcourRegion start; // START
    private ParcourRegion end;   // END

    // Numbered checkpoints (0..N-1)
    private final Map<Integer, ParcourRegion> checkpointsByOrder = new TreeMap<>();

    // Exit spawn (for /parcour leave)
    private String exitWorld;
    private double exitX, exitY, exitZ;
    private float exitYaw, exitPitch;

    public ParcourDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() { return id; }

    // ===== START / END =====
    public Optional<ParcourRegion> startRegion() { return Optional.ofNullable(start); }
    public void setStartRegion(ParcourRegion r) { this.start = r; }
    public boolean clearStartRegion() { boolean had = this.start != null; this.start = null; return had; }

    public Optional<ParcourRegion> endRegion() { return Optional.ofNullable(end); }
    public void setEndRegion(ParcourRegion r) { this.end = r; }
    public boolean clearEndRegion() { boolean had = this.end != null; this.end = null; return had; }

    // ===== Checkpoints =====
    public Collection<ParcourRegion> checkpoints() {
        return new ArrayList<>(checkpointsByOrder.values());
    }

    public Optional<ParcourRegion> checkpoint(int order) {
        return Optional.ofNullable(checkpointsByOrder.get(order));
    }

    public void putCheckpoint(ParcourRegion r) {
        if (r.type() != ParcourRegionType.CHECKPOINT) throw new IllegalArgumentException("not a checkpoint");
        checkpointsByOrder.put(r.order(), r);
    }

    public boolean removeCheckpoint(int order) {
        return checkpointsByOrder.remove(order) != null;
    }

    public SortedSet<Integer> orders() {
        return new TreeSet<>(checkpointsByOrder.keySet());
    }

    public int totalRegions() {
        return (start != null ? 1 : 0) + (end != null ? 1 : 0) + checkpointsByOrder.size();
    }

    // ===== Exit spawn =====
    public void setExitSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.exitWorld = world;
        this.exitX = x; this.exitY = y; this.exitZ = z;
        this.exitYaw = yaw; this.exitPitch = pitch;
    }

    public Optional<Location> exitSpawn() {
        if (exitWorld == null) return Optional.empty();
        World w = Bukkit.getWorld(exitWorld);
        if (w == null) return Optional.empty();
        return Optional.of(new Location(w, exitX, exitY, exitZ, exitYaw, exitPitch));
    }

    public Location fallbackWorldSpawn() {
        World w = Bukkit.getWorlds().get(0);
        return w.getSpawnLocation();
    }
}
