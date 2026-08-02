package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Samples the visible part of a cuboid outline without iterating over distant edges.
 */
final class CuboidOutlineSampler {

    private CuboidOutlineSampler() {
    }

    static Set<BlockPoint> sample(
            CuboidBounds bounds,
            BlockPoint viewer,
            int maxDistance,
            int requestedStep,
            int maxBlocks
    ) {
        int distance = Math.max(1, maxDistance);
        int limit = Math.max(1, maxBlocks);
        int step = Math.max(1, requestedStep);

        LinkedHashSet<BlockPoint> sampled = sampleAtStep(bounds, viewer, distance, step);
        while (sampled.size() > limit) {
            int nextStep = Math.max(step + 1,
                    (int) Math.ceil((double) step * sampled.size() / limit));
            LinkedHashSet<BlockPoint> next = sampleAtStep(bounds, viewer, distance, nextStep);
            if (next.size() >= sampled.size()) {
                sampled = next;
                break;
            }
            step = nextStep;
            sampled = next;
        }

        if (sampled.size() <= limit) {
            return Collections.unmodifiableSet(sampled);
        }
        LinkedHashSet<BlockPoint> capped = new LinkedHashSet<>(limit);
        for (BlockPoint point : sampled) {
            capped.add(point);
            if (capped.size() == limit) {
                break;
            }
        }
        return Collections.unmodifiableSet(capped);
    }

    static boolean isVisible(BlockPoint point, BlockPoint viewer, int maxDistance) {
        int distance = Math.max(1, maxDistance);
        return Math.abs((long) point.x() - viewer.x()) <= distance
                && Math.abs((long) point.y() - viewer.y()) <= distance
                && Math.abs((long) point.z() - viewer.z()) <= distance;
    }

    private static LinkedHashSet<BlockPoint> sampleAtStep(
            CuboidBounds bounds,
            BlockPoint viewer,
            int distance,
            int step
    ) {
        LinkedHashSet<BlockPoint> points = new LinkedHashSet<>();
        int[] xs = {bounds.minX(), bounds.maxX()};
        int[] ys = {bounds.minY(), bounds.maxY()};
        int[] zs = {bounds.minZ(), bounds.maxZ()};

        for (int y : ys) {
            for (int z : zs) {
                sampleX(points, bounds.minX(), bounds.maxX(), y, z, viewer, distance, step);
            }
        }
        for (int x : xs) {
            for (int z : zs) {
                sampleY(points, bounds.minY(), bounds.maxY(), x, z, viewer, distance, step);
            }
        }
        for (int x : xs) {
            for (int y : ys) {
                sampleZ(points, bounds.minZ(), bounds.maxZ(), x, y, viewer, distance, step);
            }
        }
        return points;
    }

    private static void sampleX(
            Set<BlockPoint> output,
            int globalMin,
            int globalMax,
            int y,
            int z,
            BlockPoint viewer,
            int distance,
            int step
    ) {
        if (!within(y, viewer.y(), distance) || !within(z, viewer.z(), distance)) {
            return;
        }
        int start = Math.max(globalMin, saturatingSubtract(viewer.x(), distance));
        int end = Math.min(globalMax, saturatingAdd(viewer.x(), distance));
        sampleRange(output, globalMin, globalMax, start, end, step,
                value -> new BlockPoint(value, y, z));
    }

    private static void sampleY(
            Set<BlockPoint> output,
            int globalMin,
            int globalMax,
            int x,
            int z,
            BlockPoint viewer,
            int distance,
            int step
    ) {
        if (!within(x, viewer.x(), distance) || !within(z, viewer.z(), distance)) {
            return;
        }
        int start = Math.max(globalMin, saturatingSubtract(viewer.y(), distance));
        int end = Math.min(globalMax, saturatingAdd(viewer.y(), distance));
        sampleRange(output, globalMin, globalMax, start, end, step,
                value -> new BlockPoint(x, value, z));
    }

    private static void sampleZ(
            Set<BlockPoint> output,
            int globalMin,
            int globalMax,
            int x,
            int y,
            BlockPoint viewer,
            int distance,
            int step
    ) {
        if (!within(x, viewer.x(), distance) || !within(y, viewer.y(), distance)) {
            return;
        }
        int start = Math.max(globalMin, saturatingSubtract(viewer.z(), distance));
        int end = Math.min(globalMax, saturatingAdd(viewer.z(), distance));
        sampleRange(output, globalMin, globalMax, start, end, step,
                value -> new BlockPoint(x, y, value));
    }

    private static void sampleRange(
            Set<BlockPoint> output,
            int globalMin,
            int globalMax,
            int visibleMin,
            int visibleMax,
            int step,
            PointFactory factory
    ) {
        if (visibleMin > visibleMax) {
            return;
        }

        int remainder = Math.floorMod((long) visibleMin - globalMin, step);
        int first = remainder == 0 ? visibleMin : saturatingAdd(visibleMin, step - remainder);
        for (int value = first; value <= visibleMax;) {
            output.add(factory.create(value));
            if (value > Integer.MAX_VALUE - step) {
                break;
            }
            value += step;
        }

        if (globalMin >= visibleMin && globalMin <= visibleMax) {
            output.add(factory.create(globalMin));
        }
        if (globalMax >= visibleMin && globalMax <= visibleMax) {
            output.add(factory.create(globalMax));
        }
    }

    private static boolean within(int value, int center, int distance) {
        return Math.abs((long) value - center) <= distance;
    }

    private static int saturatingAdd(int value, int amount) {
        long result = (long) value + amount;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static int saturatingSubtract(int value, int amount) {
        long result = (long) value - amount;
        return result < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) result;
    }

    @FunctionalInterface
    private interface PointFactory {
        BlockPoint create(int value);
    }
}

record BlockPoint(int x, int y, int z) {
}

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

    Set<BlockPoint> corners() {
        LinkedHashSet<BlockPoint> corners = new LinkedHashSet<>(8);
        int[] xs = {minX, maxX};
        int[] ys = {minY, maxY};
        int[] zs = {minZ, maxZ};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    corners.add(new BlockPoint(x, y, z));
                }
            }
        }
        return Collections.unmodifiableSet(corners);
    }
}
