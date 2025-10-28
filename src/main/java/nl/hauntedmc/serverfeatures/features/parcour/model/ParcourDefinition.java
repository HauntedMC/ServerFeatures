// File: nl/hauntedmc/serverfeatures/features/parcour/model/ParcourDefinition.java
package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public final class ParcourDefinition {
    private final String id;
    private final Map<Integer, ParcourRegion> regionsByOrder = new TreeMap<>();
    // Exit spawn (for /parcour leave)
    private String exitWorld;
    private double exitX, exitY, exitZ;
    private float exitYaw, exitPitch;

    public ParcourDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() { return id; }

    public Collection<ParcourRegion> regions() {
        return new ArrayList<>(regionsByOrder.values());
    }

    public Optional<ParcourRegion> regionByOrder(int order) {
        return Optional.ofNullable(regionsByOrder.get(order));
    }

    public void putRegion(ParcourRegion r) {
        regionsByOrder.put(r.order(), r);
    }

    public boolean removeRegion(int order) {
        return regionsByOrder.remove(order) != null;
    }

    public int totalOrders() {
        return regionsByOrder.size();
    }

    public Optional<ParcourRegion> startRegion() {
        return regionByOrder(0).filter(r -> r.type() == ParcourRegionType.START);
    }

    public Optional<ParcourRegion> endRegion() {
        // end region is the highest order with type END
        return regionsByOrder.values().stream()
                .filter(r -> r.type() == ParcourRegionType.END)
                .max(Comparator.comparingInt(ParcourRegion::order));
    }

    public SortedSet<Integer> orders() {
        return new TreeSet<>(regionsByOrder.keySet());
    }

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
