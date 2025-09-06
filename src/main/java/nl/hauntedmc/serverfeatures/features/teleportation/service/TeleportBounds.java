package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.Optional;

/**
 * Boundary logic:
 * - Outer bounds: WorldBorder (recommended). Can be disabled via respect_world_border=false.
 * - Inner bounds: configured rectangle (min_x..max_x, min_z..max_z) as a *reserved/no-TP* region.
 *   NOTE: Inner bounds are only used by RandomTP (not by direct /tppos).
 */
public class TeleportBounds {

    public record Rect(int minX, int maxX, int minZ, int maxZ) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
        public Rect normalized() {
            int nMinX = Math.min(minX, maxX);
            int nMaxX = Math.max(minX, maxX);
            int nMinZ = Math.min(minZ, maxZ);
            int nMaxZ = Math.max(minZ, maxZ);
            return new Rect(nMinX, nMaxX, nMinZ, nMaxZ);
        }
        public Map<String,String> asPlaceholders() {
            Rect n = normalized();
            return Map.of(
                    "min_x", String.valueOf(n.minX),
                    "max_x", String.valueOf(n.maxX),
                    "min_z", String.valueOf(n.minZ),
                    "max_z", String.valueOf(n.maxZ)
            );
        }
    }

    private final Teleportation feature;

    public TeleportBounds(Teleportation feature) {
        this.feature = feature;
    }

    public boolean useWorldBorder() {
        Object v = feature.getConfigHandler().getSetting("respect_world_border");
        return (v instanceof Boolean b) ? b : true;
    }

    /** Inner rectangle as reserved/no-teleport area. Disabled if 0..0..0..0. */
    public Optional<Rect> innerRect() {
        int minX = getInt("min_x", 0);
        int maxX = getInt("max_x", 0);
        int minZ = getInt("min_z", 0);
        int maxZ = getInt("max_z", 0);
        Rect r = new Rect(minX, maxX, minZ, maxZ).normalized();
        if (r.minX == 0 && r.maxX == 0 && r.minZ == 0 && r.maxZ == 0) return Optional.empty();
        return Optional.of(r);
    }

    public Rect outerRect(World world) {
        if (!useWorldBorder()) {
            int big = 30_000_000; // MC hard limit safeguard
            return new Rect(-big, big, -big, big);
        }
        var wb = world.getWorldBorder();
        Location c = wb.getCenter();
        double half = wb.getSize() / 2.0d;
        int minX = (int) Math.floor(c.getX() - half);
        int maxX = (int) Math.ceil(c.getX() + half);
        int minZ = (int) Math.floor(c.getZ() - half);
        int maxZ = (int) Math.ceil(c.getZ() + half);
        return new Rect(minX, maxX, minZ, maxZ);
    }

    /** Outer-only check: used by /tppos. */
    public boolean withinOuter(World world, int x, int z) {
        return outerRect(world).contains(x, z);
    }

    private int getInt(String key, int def) {
        Object v = feature.getConfigHandler().getSetting(key);
        return (v instanceof Number n) ? n.intValue() : def;
    }
}
