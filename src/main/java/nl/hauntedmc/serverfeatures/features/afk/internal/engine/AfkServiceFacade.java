package nl.hauntedmc.serverfeatures.features.afk.internal.engine;

public interface AfkServiceFacade {
    double moveThreshold();
    float rotateThreshold();
    long comboWindowMs();
    boolean antiEnabled();
    long antiWindowMs();
    int antiMinSamples();
    long antiMeanMinMs();
    long antiMeanMaxMs();
    long antiStddevMaxMs();
    boolean isAfkCommand(String raw);
    long antiLockMs();
    double verticalEpsilon();
}
