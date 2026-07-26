package nl.hauntedmc.serverfeatures.framework.loader.dependency;

import nl.hauntedmc.serverfeatures.framework.loader.FeatureDescriptor;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class FeatureDependentResolver {

    private FeatureDependentResolver() {
    }

    static List<String> getDependentFeatures(
            String targetKey,
            Set<String> loadedFeatureNames,
            Function<String, FeatureDescriptor> descriptorProvider,
            Function<String, String> featureKeyResolver
    ) {
        if (targetKey == null) {
            return List.of();
        }

        return loadedFeatureNames.stream()
                .filter(name -> {
                    FeatureDescriptor descriptor = descriptorProvider.apply(name);
                    if (descriptor == null) {
                        return false;
                    }
                    for (String dependency : descriptor.featureDependencies()) {
                        String dependencyKey = featureKeyResolver.apply(dependency);
                        if (targetKey.equals(dependencyKey)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }
}
