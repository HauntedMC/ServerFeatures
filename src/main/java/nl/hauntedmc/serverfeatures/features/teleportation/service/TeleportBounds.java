package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.Optional;

/**
 * Boundary logic:
 * - Effective outer = intersection(WorldBorder if respected, configured outer if valid; otherwise "infinite"/WB-only)
 * - Inner = reserved/no-teleport rectangle (RandomTP only).
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

        public boolean encloses(Rect other) {
            return this.minX <= other.minX && this.maxX >= other.maxX
                    && this.minZ <= other.minZ && this.maxZ >= other.maxZ;
        }

        public Rect intersect(Rect b) {
            int iMinX = Math.max(this.minX, b.minX);
            int iMaxX = Math.min(this.maxX, b.maxX);
            int iMinZ = Math.max(this.minZ, b.minZ);
            int iMaxZ = Math.min(this.maxZ, b.maxZ);
            if (iMinX > iMaxX || iMinZ > iMaxZ) return new Rect(1, 0, 1, 0); // empty
            return new Rect(iMinX, iMaxX, iMinZ, iMaxZ);
        }

        public boolean isEmpty() {
            return minX > maxX || minZ > maxZ;
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

    /** Inner rectangle (no-TP reserved). Disabled if 0..0..0..0. */
    public Optional<Rect> innerRect() {
        Rect r = readRect("bounds", "inner").normalized();
        if (r.minX == 0 && r.maxX == 0 && r.minZ == 0 && r.maxZ == 0) return Optional.empty();
        return Optional.of(r);
    }

    /**
     * Configured outer rectangle. Disabled if 0..0..0..0 or invalid.
     * Invalid = does NOT fully enclose inner (if inner present).
     */
    public Optional<Rect> configuredOuterRect() {
        Rect outer = readRect("bounds", "outer").normalized();
        if (outer.minX == 0 && outer.maxX == 0 && outer.minZ == 0 && outer.maxZ == 0) {
            return Optional.empty();
        }
        Optional<Rect> innerOpt = innerRect();
        if (innerOpt.isPresent() && !outer.encloses(innerOpt.get())) {
            // per spec: if any side of outer is "inside" inner, ignore outer entirely
            return Optional.empty();
        }
        return Optional.of(outer);
    }

    /** WorldBorder rect for a world. */
    public Rect worldBorderRect(World world) {
        var wb = world.getWorldBorder();
        Location c = wb.getCenter();
        double half = wb.getSize() / 2.0d;
        int minX = (int) Math.floor(c.getX() - half);
        int maxX = (int) Math.ceil(c.getX() + half);
        int minZ = (int) Math.floor(c.getZ() - half);
        int maxZ = (int) Math.ceil(c.getZ() + half);
        return new Rect(minX, maxX, minZ, maxZ);
    }

    private Rect infiniteRect() {
        int big = 30_000_000; // vanilla hard limit safety
        return new Rect(-big, big, -big, big);
    }

    /** Effective outer = (WB if respected) ∩ (configured outer if valid, else infinite). */
    public Rect effectiveOuterRect(World world) {
        Optional<Rect> configured = configuredOuterRect();
        boolean respectWB = useWorldBorder();
        Rect candidate = configured.orElse(infiniteRect());
        return respectWB ? worldBorderRect(world).intersect(candidate) : candidate;
    }

    /** Used by /tppos (unless bypass). */
    public boolean withinEffectiveOuter(World world, int x, int z) {
        Rect eff = effectiveOuterRect(world);
        return !eff.isEmpty() && eff.contains(x, z);
    }

    /* ------------------- robust config reads ------------------- */

    /**
     * Try reading a nested map at "rootKey" and then "subKey".
     * If that fails, fall back to dotted paths like "rootKey.subKey.min_x".
     */
    @SuppressWarnings("unchecked")
    private Rect readRect(String rootKey, String subKey) {
        // Attempt 1: nested map (YAML → Map)
        Object root = feature.getConfigHandler().getSetting(rootKey);
        if (root instanceof Map<?, ?> r) {
            Object sub = r.get(subKey);
            if (sub instanceof Map<?, ?> s) {
                Integer minX = asInt(s.get("min_x"));
                Integer maxX = asInt(s.get("max_x"));
                Integer minZ = asInt(s.get("min_z"));
                Integer maxZ = asInt(s.get("max_z"));
                if (minX != null && maxX != null && minZ != null && maxZ != null) {
                    return new Rect(minX, maxX, minZ, maxZ);
                }
            }
        }

        // Attempt 2: dotted path lookup (common with flattened config handlers)
        Integer minX = asInt(feature.getConfigHandler().getSetting(rootKey + "." + subKey + ".min_x"));
        Integer maxX = asInt(feature.getConfigHandler().getSetting(rootKey + "." + subKey + ".max_x"));
        Integer minZ = asInt(feature.getConfigHandler().getSetting(rootKey + "." + subKey + ".min_z"));
        Integer maxZ = asInt(feature.getConfigHandler().getSetting(rootKey + "." + subKey + ".max_z"));
        if (minX != null && maxX != null && minZ != null && maxZ != null) {
            return new Rect(minX, maxX, minZ, maxZ);
        }

        // Fallback: zeros (disabled)
        return new Rect(0, 0, 0, 0);
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        }
        return null;
    }
}
