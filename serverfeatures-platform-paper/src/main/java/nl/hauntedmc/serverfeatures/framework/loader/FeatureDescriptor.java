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
        this(registryName, featureClassName, (Class<? extends BaseMeta>) null, featureName,
                featureVersion, featureDependencies, pluginDependencies);
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
                featureName, featureVersion, featureDependencies, pluginDependencies);
    }

    public FeatureDescriptor(
            String registryName,
            String featureClassName,
            Class<? extends BaseMeta> metaClass,
            String featureName,
            String featureVersion,
            Set<String> featureDependencies,
            Set<String> pluginDependencies
    ) {
        this.registryName = registryName;
        this.featureClassName = featureClassName;
        this.metaClass = metaClass;
        this.featureName = featureName == null ? "" : featureName;
        this.featureVersion = featureVersion == null ? "" : featureVersion;
        this.featureDependencies = normalizeDependencies(featureDependencies, registryName);
        this.pluginDependencies = normalizeDependencies(pluginDependencies, null);
    }

    public BaseMeta createMeta() {
        if (metaClass != null) {
            try {
                return metaClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to the immutable descriptor snapshot.
            }
        }
        return new StaticMeta(
                featureName,
                featureVersion,
                List.copyOf(featureDependencies),
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
        public List<String> getPluginDependencies() {
            return pluginDependencies;
        }
    }
}
