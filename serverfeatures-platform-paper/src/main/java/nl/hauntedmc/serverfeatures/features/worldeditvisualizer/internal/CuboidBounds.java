package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

record CuboidBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    CuboidBounds {
        int lowerX = Math.min(minX, maxX);
        int upperX = Math.max(minX, maxX);
        int lowerY = Math.min(minY, maxY);
        int upperY = Math.max(minY, maxY);
        int lowerZ = Math.min(minZ, maxZ);
        int upperZ = Math.max(minZ, maxZ);
        minX = lowerX;
        maxX = upperX;
        minY = lowerY;
        maxY = upperY;
        minZ = lowerZ;
        maxZ = upperZ;
    }

    Set<VisualPoint> corners() {
        LinkedHashSet<VisualPoint> corners = new LinkedHashSet<>(8);
        int[] xs = {minX, maxX};
        int[] ys = {minY, maxY};
        int[] zs = {minZ, maxZ};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    corners.add(VisualPoint.blockCenter(x, y, z));
                }
            }
        }
        return Collections.unmodifiableSet(corners);
    }
}
