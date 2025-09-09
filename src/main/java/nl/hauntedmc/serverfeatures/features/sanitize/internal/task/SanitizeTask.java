package nl.hauntedmc.serverfeatures.features.sanitize.internal.task;

public interface SanitizeTask {
    String name();
    SanitizeResult run(SanitizeContext ctx) throws Exception;
}
