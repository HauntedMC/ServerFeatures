package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureDescriptorTest {

    @Test
    void normalizesDependenciesAndSkipsSelfDependency() {
        FeatureDescriptor descriptor = new FeatureDescriptor(
                "FeatureA",
                "a.b.FeatureA",
                "Feature A",
                "1.0.0",
                new LinkedHashSet<>(List.of(" dep1 ", "dep1", " Dep2 ", "featurea", "FEATUREA")),
                new LinkedHashSet<>(List.of(" Vault ", "", "Vault", " "))
        );

        assertEquals(Set.of("dep1", "Dep2"), descriptor.featureDependencies());
        assertEquals(Set.of("Vault"), descriptor.pluginDependencies());
    }

    @Test
    void emptyOrNullDependenciesBecomeEmptySet() {
        FeatureDescriptor descriptor = new FeatureDescriptor("x", "x.C", "x", "1", null, Set.of());
        assertTrue(descriptor.featureDependencies().isEmpty());
        assertTrue(descriptor.pluginDependencies().isEmpty());
    }

    @Test
    void returnedSetsAreUnmodifiable() {
        FeatureDescriptor descriptor = new FeatureDescriptor("x", "x.C", "x", "1", Set.of("a"), Set.of("b"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.featureDependencies().add("c"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.pluginDependencies().add("d"));
    }
}
