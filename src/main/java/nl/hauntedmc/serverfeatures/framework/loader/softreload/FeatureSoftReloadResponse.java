package nl.hauntedmc.serverfeatures.framework.loader.softreload;

public record FeatureSoftReloadResponse(
        FeatureSoftReloadResult result,
        String feature
) {
    public boolean success() { return result == FeatureSoftReloadResult.SUCCESS; }
}
