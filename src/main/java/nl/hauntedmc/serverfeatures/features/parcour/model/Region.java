// File: nl/hauntedmc/serverfeatures/features/parcour/model/Region.java
package nl.hauntedmc.serverfeatures.features.parcour.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class Region {
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public Region(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String worldName() {
        return worldName;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World w = loc.getWorld();
        if (!w.getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public Location center(org.bukkit.Server server) {
        World w = server.getWorld(worldName);
        if (w == null) return null;
        double cx = (minX + maxX) / 2.0 + 0.5;
        double cy = minY + 1.0;
        double cz = (minZ + maxZ) / 2.0 + 0.5;
        return new Location(w, cx, cy, cz, 0f, 0f);
    }
}
