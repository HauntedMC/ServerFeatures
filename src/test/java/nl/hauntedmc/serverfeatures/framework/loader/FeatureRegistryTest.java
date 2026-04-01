package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureRegistryTest {

    @Test
    void registersAndDeregistersAvailableAndLoadedFeatures() {
        FeatureRegistry registry = new FeatureRegistry();
        FeatureDescriptor descriptor = new FeatureDescriptor("alpha", "x.Alpha", "Alpha", "1", java.util.Set.of(), java.util.Set.of());

        registry.registerAvailableFeature(descriptor);
        registry.registerLoadedFeature("alpha", null);

        assertEquals(descriptor, registry.getAvailableFeature("alpha"));
        assertTrue(registry.isFeatureLoaded("alpha"));
        assertNull(registry.getLoadedFeature("alpha"));

        registry.deregisterLoadedFeature("alpha");
        registry.deregisterAvailableFeature("alpha");

        assertFalse(registry.isFeatureLoaded("alpha"));
        assertNull(registry.getAvailableFeature("alpha"));
    }

    @Test
    void loadedFeatureNamesAreDefensiveAndUnmodifiable() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.registerLoadedFeature("one", null);
        var names = registry.getLoadedFeatureNames();

        assertTrue(names.contains("one"));
        assertThrows(UnsupportedOperationException.class, () -> names.add("two"));
    }

    @Test
    void availableFeaturesMapIsUnmodifiable() {
        FeatureRegistry registry = new FeatureRegistry();
        Map<String, FeatureDescriptor> map = registry.getAvailableFeatures();
        assertThrows(UnsupportedOperationException.class, () -> map.put("x", null));
    }
}
