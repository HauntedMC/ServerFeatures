package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.framework.loader.dependency.DependencyCheckResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureDependencyDiagnosticsTest {

    @Test
    void diagnoseCollectsMissingPluginsAndFeatureDependenciesRecursively() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("B", "AliasMissing"), Set.of()));
        descriptors.put("B", new FeatureDescriptor("B", "x.B", "B", "1", Set.of("C"), Set.of("Vault")));
        descriptors.put("C", new FeatureDescriptor("C", "x.C", "C", "1", Set.of(), Set.of()));

        DependencyCheckResult result = FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                "A",
                name -> switch (name) {
                    case "A", "B", "C" -> name;
                    default -> null;
                },
                descriptors::get,
                name -> name.equals("A"),
                name -> name.equals("B") ? Set.of("Vault") : Set.of()
        );

        assertEquals(Set.of("Vault"), result.missingPluginDependencies());
        assertEquals(Set.of("B", "AliasMissing", "C"), result.missingFeatureDependencies());
    }

    @Test
    void diagnoseReturnsFeatureAsMissingWhenRootCannotBeResolved() {
        DependencyCheckResult result = FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                "Unknown",
                name -> null,
                name -> null,
                name -> false,
                name -> Set.of()
        );

        assertTrue(result.missingPluginDependencies().isEmpty());
        assertEquals(Set.of("Unknown"), result.missingFeatureDependencies());
    }
}
