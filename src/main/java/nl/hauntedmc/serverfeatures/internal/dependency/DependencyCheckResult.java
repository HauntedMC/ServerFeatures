package nl.hauntedmc.serverfeatures.internal.dependency;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DependencyCheckResult {
    private final Set<String> missingPluginDependencies;
    private final Set<String> missingFeatureDependencies;

    public DependencyCheckResult(Set<String> missingPlugins, Set<String> missingFeatures) {
        this.missingPluginDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(missingPlugins));
        this.missingFeatureDependencies = Collections.unmodifiableSet(new LinkedHashSet<>(missingFeatures));
    }

    public Set<String> missingPluginDependencies() { return missingPluginDependencies; }
    public Set<String> missingFeatureDependencies() { return missingFeatureDependencies; }
    public boolean ok() { return missingPluginDependencies.isEmpty() && missingFeatureDependencies.isEmpty(); }
}
