package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    @Test
    void createsFreshMetadataForEveryFeatureContext() {
        FeatureDescriptor descriptor = new FeatureDescriptor(
                "Example",
                "example.Example",
                ExampleMeta.class,
                "Example",
                "2.0",
                Set.of(),
                Set.of(),
                Set.of()
        );

        BaseMeta first = descriptor.createMeta();
        BaseMeta second = descriptor.createMeta();

        assertTrue(first instanceof ExampleMeta);
        assertTrue(second instanceof ExampleMeta);
        assertNotSame(first, second);
    }

    @Test
    void fallsBackToDescriptorSnapshotWhenMetadataCannotBeConstructed() {
        FeatureDescriptor descriptor = new FeatureDescriptor(
                "Fallback",
                "example.Fallback",
                BrokenMeta.class,
                "Fallback",
                "3.0",
                Set.of("Dependency"),
                Set.of(),
                Set.of("Plugin")
        );

        BaseMeta meta = descriptor.createMeta();

        assertEquals("Fallback", meta.getFeatureName());
        assertEquals("3.0", meta.getFeatureVersion());
        assertEquals(List.of("Dependency"), meta.getDependencies());
        assertEquals(List.of("Plugin"), meta.getPluginDependencies());
    }

    public static final class ExampleMeta implements BaseMeta {
        public ExampleMeta() {
        }

        @Override
        public String getFeatureName() {
            return "Example";
        }

        @Override
        public String getFeatureVersion() {
            return "2.0";
        }
    }

    public static final class BrokenMeta implements BaseMeta {
        public BrokenMeta() {
            throw new IllegalStateException("broken");
        }

        @Override
        public String getFeatureName() {
            return "Broken";
        }

        @Override
        public String getFeatureVersion() {
            return "0";
        }
    }
}
