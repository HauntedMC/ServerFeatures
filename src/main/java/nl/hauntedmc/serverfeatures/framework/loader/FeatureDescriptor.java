package nl.hauntedmc.serverfeatures.framework.loader;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record FeatureDescriptor(
        String registryName,
        String featureClassName,
        String featureName,
        String featureVersion,
        Set<String> featureDependencies,
        Set<String> pluginDependencies
) {
    public FeatureDescriptor(
            String registryName,
            String featureClassName,
            String featureName,
            String featureVersion,
            Set<String> featureDependencies,
            Set<String> pluginDependencies
    ) {
        this.registryName = registryName;
        this.featureClassName = featureClassName;
        this.featureName = featureName;
        this.featureVersion = featureVersion;
        this.featureDependencies = normalizeDependencies(featureDependencies, registryName);
        this.pluginDependencies = normalizeDependencies(pluginDependencies, null);
    }

    private static Set<String> normalizeDependencies(Set<String> dependencies, String selfDependencyName) {
        if (dependencies == null || dependencies.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String dependency : dependencies) {
            if (dependency == null) {
                continue;
            }

            String clean = dependency.trim();
            if (clean.isEmpty()) {
                continue;
            }

            if (selfDependencyName != null && clean.equalsIgnoreCase(selfDependencyName)) {
                continue;
            }

            normalized.add(clean);
        }

        if (normalized.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(normalized);
    }
}
