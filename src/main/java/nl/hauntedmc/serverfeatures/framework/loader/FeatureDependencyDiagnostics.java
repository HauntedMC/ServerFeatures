package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.framework.loader.dependency.DependencyCheckResult;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

final class FeatureDependencyDiagnostics {

    private FeatureDependencyDiagnostics() {
    }

    static DependencyCheckResult diagnoseDependenciesRecursively(
            String featureName,
            Function<String, String> featureKeyResolver,
            Function<String, FeatureDescriptor> descriptorProvider,
            Predicate<String> isFeatureLoaded,
            Function<String, Set<String>> missingPluginDependenciesProvider
    ) {
        String featureKey = featureKeyResolver.apply(featureName);
        if (featureKey == null) {
            return new DependencyCheckResult(Set.of(), Set.of(featureName));
        }

        Set<String> missingPlugins = new LinkedHashSet<>();
        Set<String> missingFeatures = new LinkedHashSet<>();
        collectDependencyGaps(
                featureKey,
                featureKeyResolver,
                descriptorProvider,
                isFeatureLoaded,
                missingPluginDependenciesProvider,
                new HashSet<>(),
                missingPlugins,
                missingFeatures
        );
        return new DependencyCheckResult(missingPlugins, missingFeatures);
    }

    private static void collectDependencyGaps(
            String featureName,
            Function<String, String> featureKeyResolver,
            Function<String, FeatureDescriptor> descriptorProvider,
            Predicate<String> isFeatureLoaded,
            Function<String, Set<String>> missingPluginDependenciesProvider,
            Set<String> visited,
            Set<String> missingPlugins,
            Set<String> missingFeatures
    ) {
        if (!visited.add(featureName)) {
            return;
        }

        FeatureDescriptor descriptor = descriptorProvider.apply(featureName);
        if (descriptor == null) {
            missingFeatures.add(featureName);
            return;
        }

        missingPlugins.addAll(missingPluginDependenciesProvider.apply(featureName));
        for (String dependency : descriptor.featureDependencies()) {
            String dependencyKey = featureKeyResolver.apply(dependency);
            if (dependencyKey == null) {
                missingFeatures.add(dependency);
                continue;
            }
            if (!isFeatureLoaded.test(dependencyKey)) {
                missingFeatures.add(dependencyKey);
            }
            collectDependencyGaps(
                    dependencyKey,
                    featureKeyResolver,
                    descriptorProvider,
                    isFeatureLoaded,
                    missingPluginDependenciesProvider,
                    visited,
                    missingPlugins,
                    missingFeatures
            );
        }
    }
}
