package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;

final class RuleTestFacade implements AfkServiceFacade {

    boolean antiEnabled = false;
    boolean afkCommand = false;
    double moveThreshold = 0.15D;
    float rotateThreshold = 10.0F;
    long comboWindowMs = 2_000L;
    long antiWindowMs = 10_000L;
    int antiMinSamples = 2;
    long antiMeanMinMs = 100L;
    long antiMeanMaxMs = 10_000L;
    long antiStddevMaxMs = 1_000L;
    long antiLockMs = 5_000L;
    double verticalEpsilon = 0.05D;

    @Override
    public double moveThreshold() {
        return moveThreshold;
    }

    @Override
    public float rotateThreshold() {
        return rotateThreshold;
    }

    @Override
    public long comboWindowMs() {
        return comboWindowMs;
    }

    @Override
    public boolean antiEnabled() {
        return antiEnabled;
    }

    @Override
    public long antiWindowMs() {
        return antiWindowMs;
    }

    @Override
    public int antiMinSamples() {
        return antiMinSamples;
    }

    @Override
    public long antiMeanMinMs() {
        return antiMeanMinMs;
    }

    @Override
    public long antiMeanMaxMs() {
        return antiMeanMaxMs;
    }

    @Override
    public long antiStddevMaxMs() {
        return antiStddevMaxMs;
    }

    @Override
    public boolean isAfkCommand(String raw) {
        return afkCommand;
    }

    @Override
    public long antiLockMs() {
        return antiLockMs;
    }

    @Override
    public double verticalEpsilon() {
        return verticalEpsilon;
    }
}

