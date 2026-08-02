package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import java.util.List;

/**
 * Immutable outer-boundary geometry for an inclusive cuboid block selection.
 */
final class CuboidWireframe {

    private final List<Edge> edges;
    private final List<Point> corners;

    private CuboidWireframe(List<Edge> edges, List<Point> corners) {
        this.edges = List.copyOf(edges);
        this.corners = List.copyOf(corners);
    }

    static CuboidWireframe fromInclusive(
            int firstX, int firstY, int firstZ,
            int secondX, int secondY, int secondZ
    ) {
        double minX = Math.min(firstX, secondX);
        double minY = Math.min(firstY, secondY);
        double minZ = Math.min(firstZ, secondZ);
        double maxX = Math.max(firstX, secondX) + 1.0d;
        double maxY = Math.max(firstY, secondY) + 1.0d;
        double maxZ = Math.max(firstZ, secondZ) + 1.0d;

        double lengthX = maxX - minX;
        double lengthY = maxY - minY;
        double lengthZ = maxZ - minZ;

        Point minMinMin = new Point(minX, minY, minZ);
        Point minMinMax = new Point(minX, minY, maxZ);
        Point minMaxMin = new Point(minX, maxY, minZ);
        Point minMaxMax = new Point(minX, maxY, maxZ);
        Point maxMinMin = new Point(maxX, minY, minZ);
        Point maxMinMax = new Point(maxX, minY, maxZ);
        Point maxMaxMin = new Point(maxX, maxY, minZ);
        Point maxMaxMax = new Point(maxX, maxY, maxZ);

        List<Edge> edges = List.of(
                new Edge(minMinMin, Axis.X, lengthX),
                new Edge(minMinMax, Axis.X, lengthX),
                new Edge(minMaxMin, Axis.X, lengthX),
                new Edge(minMaxMax, Axis.X, lengthX),
                new Edge(minMinMin, Axis.Y, lengthY),
                new Edge(minMinMax, Axis.Y, lengthY),
                new Edge(maxMinMin, Axis.Y, lengthY),
                new Edge(maxMinMax, Axis.Y, lengthY),
                new Edge(minMinMin, Axis.Z, lengthZ),
                new Edge(minMaxMin, Axis.Z, lengthZ),
                new Edge(maxMinMin, Axis.Z, lengthZ),
                new Edge(maxMaxMin, Axis.Z, lengthZ)
        );
        List<Point> corners = List.of(
                minMinMin, minMinMax, minMaxMin, minMaxMax,
                maxMinMin, maxMinMax, maxMaxMin, maxMaxMax
        );
        return new CuboidWireframe(edges, corners);
    }

    List<Edge> edges() {
        return edges;
    }

    List<Point> corners() {
        return corners;
    }

    enum Axis {
        X,
        Y,
        Z
    }

    record Point(double x, double y, double z) { }

    record Edge(Point origin, Axis axis, double length) {
        Edge {
            if (origin == null || axis == null) {
                throw new IllegalArgumentException("Edge origin and axis are required");
            }
            if (!Double.isFinite(length) || length <= 0.0d) {
                throw new IllegalArgumentException("Edge length must be finite and positive");
            }
        }
    }
}
