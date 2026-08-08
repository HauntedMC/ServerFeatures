package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record FeatureDescriptor(
        String registryName,
        String featureClassName,
        Class<? extends BaseMeta> metaClass,
        String featureName,
        String featureVersion,
        Set<String> featureDependencies,
        Set<String> optionalFeatureDependencies,
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
        this(registryName, featureClassName, null, featureName, featureVersion,
                featureDependencies, Set.of(), pluginDependencies);
    }

    public FeatureDescriptor(
            String registryName,
            String featureClassName,
            String featureName,
            String featureVersion,
            Set<String> featureDependencies,
            Set<String> optionalFeatureDependencies,
            Set<String> pluginDependencies
    ) {
        this(registryName, featureClassName, null, featureName, featureVersion,
                featureDependencies, optionalFeatureDependencies, pluginDependencies);
    }

    public FeatureDescriptor(
            String registryName,
            String featureClassName,
            BaseMeta meta,
            String featureName,
            String featureVersion,
            Set<String> featureDependencies,
            Set<String> pluginDependencies
    ) {
        this(registryName, featureClassName,
                meta == null ? null : meta.getClass().asSubclass(BaseMeta.class),
                featureName, featureVersion, featureDependencies,
                meta == null ? Set.of() : new LinkedHashSet<>(meta.getOptionalDependencies()),
                pluginDependencies);
    }

    public FeatureDescriptor {
        featureName = featureName == null ? "" : featureName;
        featureVersion = featureVersion == null ? "" : featureVersion;
        featureDependencies = normalizeDependencies(featureDependencies, registryName);
        optionalFeatureDependencies = normalizeDependencies(optionalFeatureDependencies, registryName);
        if (!featureDependencies.isEmpty() && !optionalFeatureDependencies.isEmpty()) {
            Set<String> requiredDependencies = featureDependencies;
            LinkedHashSet<String> optional = new LinkedHashSet<>(optionalFeatureDependencies);
            optional.removeIf(candidate -> requiredDependencies.stream().anyMatch(candidate::equalsIgnoreCase));
            optionalFeatureDependencies = optional.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(optional);
        }
        pluginDependencies = normalizeDependencies(pluginDependencies, null);
    }

    /**
     * Creates an immutable metadata snapshot for a feature scope. The runtime deliberately does not
     * instantiate per-feature meta companions here: discovery has already validated and normalized
     * their values into this descriptor.
     */
    public BaseMeta createMeta() {
        return new StaticMeta(
                featureName,
                featureVersion,
                List.copyOf(featureDependencies),
                List.copyOf(optionalFeatureDependencies),
                List.copyOf(pluginDependencies)
        );
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
            if (clean.isEmpty() || selfDependencyName != null && clean.equalsIgnoreCase(selfDependencyName)) {
                continue;
            }
            normalized.add(clean);
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }

    private record StaticMeta(
            String featureName,
            String featureVersion,
            List<String> dependencies,
            List<String> optionalDependencies,
            List<String> pluginDependencies
    ) implements BaseMeta {
        @Override
        public String getFeatureName() {
            return featureName;
        }

        @Override
        public String getFeatureVersion() {
            return featureVersion;
        }

        @Override
        public List<String> getDependencies() {
            return dependencies;
        }

        @Override
        public List<String> getOptionalDependencies() {
            return optionalDependencies;
        }

        @Override
        public List<String> getPluginDependencies() {
            return pluginDependencies;
        }
    }
}
