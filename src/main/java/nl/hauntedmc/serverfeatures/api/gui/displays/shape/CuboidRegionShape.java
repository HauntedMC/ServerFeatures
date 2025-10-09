package nl.hauntedmc.serverfeatures.api.gui.displays.shape;

import org.bukkit.util.Vector;

import java.util.*;

/**
 * Axis-aligned cuboid region shape, with optional named points (e.g., pos1/pos2).
 * Coordinates are in world-space block coordinates; centers are shifted by +0.5.
 */
public final class CuboidRegionShape implements RegionShape {

    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<String, Vector> named;

    public CuboidRegionShape(int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ,
                             Map<String, Vector> namedPointsOrNull) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.named = namedPointsOrNull == null ? Map.of() : Map.copyOf(namedPointsOrNull);
    }

    @Override
    public List<Vector> cornerCenters() {
        List<Vector> corners = new ArrayList<>(8);
        int[] xs = new int[]{minX, maxX};
        int[] ys = new int[]{minY, maxY};
        int[] zs = new int[]{minZ, maxZ};
        for (int x : xs) for (int y : ys) for (int z : zs) {
            corners.add(new Vector(x + 0.5, y + 0.5, z + 0.5));
        }
        return corners;
    }

    @Override
    public List<Vector> sampleEdgePoints(double stepBlocks) {
        List<Vector> out = new ArrayList<>();
        int[] xs = new int[]{minX, maxX};
        int[] ys = new int[]{minY, maxY};
        int[] zs = new int[]{minZ, maxZ};

        // X-edges
        for (int y : ys) for (int z : zs) {
            for (double x = minX; x <= maxX + 1e-6; x += stepBlocks) {
                out.add(new Vector(x + 0.5, y + 0.5, z + 0.5));
            }
        }
        // Y-edges
        for (int x : xs) for (int z : zs) {
            for (double y = minY; y <= maxY + 1e-6; y += stepBlocks) {
                out.add(new Vector(x + 0.5, y + 0.5, z + 0.5));
            }
        }
        // Z-edges
        for (int x : xs) for (int y : ys) {
            for (double z = minZ; z <= maxZ + 1e-6; z += stepBlocks) {
                out.add(new Vector(x + 0.5, y + 0.5, z + 0.5));
            }
        }
        return out;
    }

    @Override
    public Map<String, Vector> namedPoints() {
        return named;
    }
}
