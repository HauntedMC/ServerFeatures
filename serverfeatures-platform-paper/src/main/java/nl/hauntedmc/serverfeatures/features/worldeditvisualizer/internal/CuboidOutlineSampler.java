package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Samples the visible part of a cuboid outline at sub-block precision without
 * iterating across distant sections of very large selections.
 */
final class CuboidOutlineSampler {

    private static final double MIN_STEP = 0.05;
    private static final double EPSILON = 1.0e-9;
    private static final int MAX_ADJUSTMENTS = 8;

    private CuboidOutlineSampler() {
    }

    static Set<VisualPoint> sample(
            CuboidBounds bounds,
            VisualPoint viewer,
            double maxDistance,
            double requestedStep,
            int maxPoints
    ) {
        double distance = finitePositive(maxDistance, 1.0);
        int limit = Math.max(1, maxPoints);
        double step = Math.max(MIN_STEP, finitePositive(requestedStep, 1.0));

        double estimated = estimatePointCount(bounds, viewer, distance, step);
        if (estimated > limit) {
            step *= estimated / limit;
        }

        LinkedHashSet<VisualPoint> sampled = new LinkedHashSet<>();
        for (int attempt = 0; attempt < MAX_ADJUSTMENTS; attempt++) {
            sampled = sampleAtStep(bounds, viewer, distance, step);
            if (sampled.size() <= limit) {
                return Collections.unmodifiableSet(sampled);
            }
            double ratio = (double) sampled.size() / limit;
            step = Math.max(step + MIN_STEP, step * ratio * 1.05);
        }

        LinkedHashSet<VisualPoint> capped = new LinkedHashSet<>(limit);
        for (VisualPoint point : sampled) {
            capped.add(point);
            if (capped.size() == limit) {
                break;
            }
        }
        return Collections.unmodifiableSet(capped);
    }

    static boolean isVisible(VisualPoint point, VisualPoint viewer, double maxDistance) {
        double distance = finitePositive(maxDistance, 1.0);
        return Math.abs(point.x() - viewer.x()) <= distance
                && Math.abs(point.y() - viewer.y()) <= distance
                && Math.abs(point.z() - viewer.z()) <= distance;
    }

    private static double estimatePointCount(
            CuboidBounds bounds,
            VisualPoint viewer,
            double distance,
            double step
    ) {
        double minX = bounds.minX() + 0.5;
        double minY = bounds.minY() + 0.5;
        double minZ = bounds.minZ() + 0.5;
        double maxX = bounds.maxX() + 0.5;
        double maxY = bounds.maxY() + 0.5;
        double maxZ = bounds.maxZ() + 0.5;
        double estimate = 0.0;

        for (double y : new double[]{minY, maxY}) {
            for (double z : new double[]{minZ, maxZ}) {
                if (within(y, viewer.y(), distance) && within(z, viewer.z(), distance)) {
                    estimate += estimateRange(minX, maxX, viewer.x(), distance, step);
                }
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double z : new double[]{minZ, maxZ}) {
                if (within(x, viewer.x(), distance) && within(z, viewer.z(), distance)) {
                    estimate += estimateRange(minY, maxY, viewer.y(), distance, step);
                }
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double y : new double[]{minY, maxY}) {
                if (within(x, viewer.x(), distance) && within(y, viewer.y(), distance)) {
                    estimate += estimateRange(minZ, maxZ, viewer.z(), distance, step);
                }
            }
        }
        return estimate;
    }

    private static double estimateRange(
            double globalMin,
            double globalMax,
            double viewerCoordinate,
            double distance,
            double step
    ) {
        double visibleMin = Math.max(globalMin, viewerCoordinate - distance);
        double visibleMax = Math.min(globalMax, viewerCoordinate + distance);
        if (visibleMin > visibleMax) {
            return 0.0;
        }
        return Math.floor((visibleMax - visibleMin) / step) + 2.0;
    }

    private static LinkedHashSet<VisualPoint> sampleAtStep(
            CuboidBounds bounds,
            VisualPoint viewer,
            double distance,
            double step
    ) {
        LinkedHashSet<VisualPoint> points = new LinkedHashSet<>();
        double minX = bounds.minX() + 0.5;
        double minY = bounds.minY() + 0.5;
        double minZ = bounds.minZ() + 0.5;
        double maxX = bounds.maxX() + 0.5;
        double maxY = bounds.maxY() + 0.5;
        double maxZ = bounds.maxZ() + 0.5;
        double[] xs = {minX, maxX};
        double[] ys = {minY, maxY};
        double[] zs = {minZ, maxZ};

        for (double y : ys) {
            for (double z : zs) {
                sampleX(points, minX, maxX, y, z, viewer, distance, step);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                sampleY(points, minY, maxY, x, z, viewer, distance, step);
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                sampleZ(points, minZ, maxZ, x, y, viewer, distance, step);
            }
        }
        return points;
    }

    private static void sampleX(
            Set<VisualPoint> output,
            double globalMin,
            double globalMax,
            double y,
            double z,
            VisualPoint viewer,
            double distance,
            double step
    ) {
        if (!within(y, viewer.y(), distance) || !within(z, viewer.z(), distance)) {
            return;
        }
        double visibleMin = Math.max(globalMin, viewer.x() - distance);
        double visibleMax = Math.min(globalMax, viewer.x() + distance);
        sampleRange(output, globalMin, globalMax, visibleMin, visibleMax, step,
                value -> VisualPoint.of(value, y, z));
    }

    private static void sampleY(
            Set<VisualPoint> output,
            double globalMin,
            double globalMax,
            double x,
            double z,
            VisualPoint viewer,
            double distance,
            double step
    ) {
        if (!within(x, viewer.x(), distance) || !within(z, viewer.z(), distance)) {
            return;
        }
        double visibleMin = Math.max(globalMin, viewer.y() - distance);
        double visibleMax = Math.min(globalMax, viewer.y() + distance);
        sampleRange(output, globalMin, globalMax, visibleMin, visibleMax, step,
                value -> VisualPoint.of(x, value, z));
    }

    private static void sampleZ(
            Set<VisualPoint> output,
            double globalMin,
            double globalMax,
            double x,
            double y,
            VisualPoint viewer,
            double distance,
            double step
    ) {
        if (!within(x, viewer.x(), distance) || !within(y, viewer.y(), distance)) {
            return;
        }
        double visibleMin = Math.max(globalMin, viewer.z() - distance);
        double visibleMax = Math.min(globalMax, viewer.z() + distance);
        sampleRange(output, globalMin, globalMax, visibleMin, visibleMax, step,
                value -> VisualPoint.of(x, y, value));
    }

    private static void sampleRange(
            Set<VisualPoint> output,
            double globalMin,
            double globalMax,
            double visibleMin,
            double visibleMax,
            double step,
            PointFactory factory
    ) {
        if (visibleMin > visibleMax + EPSILON) {
            return;
        }

        long firstIndex = Math.max(0L,
                ceilToLong(((visibleMin - globalMin) / step) - EPSILON));
        long globalLastIndex = Math.max(0L,
                floorToLong(((globalMax - globalMin) / step) + EPSILON));
        long visibleLastIndex = floorToLong(
                ((visibleMax - globalMin) / step) + EPSILON);
        long lastIndex = Math.min(globalLastIndex, visibleLastIndex);

        for (long index = firstIndex; index <= lastIndex; index++) {
            double value = globalMin + (index * step);
            if (value >= visibleMin - EPSILON && value <= visibleMax + EPSILON) {
                output.add(factory.create(value));
            }
            if (index == Long.MAX_VALUE) {
                break;
            }
        }

        if (globalMin >= visibleMin - EPSILON && globalMin <= visibleMax + EPSILON) {
            output.add(factory.create(globalMin));
        }
        if (globalMax >= visibleMin - EPSILON && globalMax <= visibleMax + EPSILON) {
            output.add(factory.create(globalMax));
        }
    }

    private static boolean within(double value, double center, double distance) {
        return Math.abs(value - center) <= distance;
    }

    private static double finitePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static long ceilToLong(double value) {
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (value <= Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return (long) Math.ceil(value);
    }

    private static long floorToLong(double value) {
        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (value <= Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return (long) Math.floor(value);
    }

    @FunctionalInterface
    private interface PointFactory {
        VisualPoint create(double value);
    }
}

record VisualPoint(long xUnits, long yUnits, long zUnits) {

    private static final double UNITS_PER_BLOCK = 4096.0;

    static VisualPoint of(double x, double y, double z) {
        return new VisualPoint(quantize(x), quantize(y), quantize(z));
    }

    static VisualPoint blockCenter(int x, int y, int z) {
        return of(x + 0.5, y + 0.5, z + 0.5);
    }

    double x() {
        return xUnits / UNITS_PER_BLOCK;
    }

    double y() {
        return yUnits / UNITS_PER_BLOCK;
    }

    double z() {
        return zUnits / UNITS_PER_BLOCK;
    }

    VisualPoint offset(double x, double y, double z) {
        return of(x() + x, y() + y, z() + z);
    }

    private static long quantize(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Visual coordinates must be finite");
        }
        double scaled = value * UNITS_PER_BLOCK;
        if (scaled >= Long.MAX_VALUE || scaled <= Long.MIN_VALUE) {
            throw new IllegalArgumentException("Visual coordinate is outside the supported range");
        }
        return Math.round(scaled);
    }
}

record BlockPoint(int x, int y, int z) {

    VisualPoint center() {
        return VisualPoint.blockCenter(x, y, z);
    }
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
