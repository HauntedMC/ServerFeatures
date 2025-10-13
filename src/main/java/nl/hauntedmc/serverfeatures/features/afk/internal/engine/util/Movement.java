package nl.hauntedmc.serverfeatures.features.afk.internal.engine.util;

public record Movement(
        double fx, double fy, double fz, float fyaw, float fpitch,
        double tx, double ty, double tz, float tyaw, float tpitch
) {
}
