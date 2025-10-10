package nl.hauntedmc.serverfeatures.framework.loader.disable;

import java.util.Set;

public record FeatureDisableResponse(
        FeatureDisableResult result,
        String feature,
        Set<String> alsoDisabledDependents
) {
    public boolean success() { return result == FeatureDisableResult.SUCCESS; }
}
