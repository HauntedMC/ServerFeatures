package nl.hauntedmc.serverfeatures.api.feature.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseMetaTest {

    @Test
    void defaultDependencyListsAreEmptyAndImmutable() {
        BaseMeta meta = new BaseMeta() {
            @Override
            public String getFeatureName() {
                return "demo";
            }

            @Override
            public String getFeatureVersion() {
                return "1.0.0";
            }
        };

        assertTrue(meta.getDependencies().isEmpty());
        assertTrue(meta.getPluginDependencies().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> meta.getDependencies().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> meta.getPluginDependencies().add("x"));
    }
}
