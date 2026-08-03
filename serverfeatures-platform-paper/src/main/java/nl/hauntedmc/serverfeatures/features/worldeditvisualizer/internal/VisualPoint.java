package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

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
