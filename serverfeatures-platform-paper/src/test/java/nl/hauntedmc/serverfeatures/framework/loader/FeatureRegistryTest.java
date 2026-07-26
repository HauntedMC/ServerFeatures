package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FeatureRegistryTest {

    @Test
    void registersAndDeregistersAvailableAndLoadedFeatures() {
        FeatureRegistry registry = new FeatureRegistry();
        FeatureDescriptor descriptor = new FeatureDescriptor("alpha", "x.Alpha", "Alpha", "1", java.util.Set.of(), java.util.Set.of());

        registry.registerAvailableFeature(descriptor);
        var feature = mock(nl.hauntedmc.serverfeatures.features.BukkitBaseFeature.class);
        registry.registerLoadedFeature("alpha", feature);

        assertEquals(descriptor, registry.getAvailableFeature("alpha"));
        assertTrue(registry.isFeatureLoaded("alpha"));
        assertEquals(feature, registry.getLoadedFeature("alpha"));

        registry.deregisterLoadedFeature("alpha");
        registry.deregisterAvailableFeature("alpha");

        assertFalse(registry.isFeatureLoaded("alpha"));
        assertNull(registry.getAvailableFeature("alpha"));
    }

    @Test
    void loadedFeatureNamesAreDefensiveAndUnmodifiable() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.registerLoadedFeature("one", mock(nl.hauntedmc.serverfeatures.features.BukkitBaseFeature.class));
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

    @Test
    void rejectsNullRegistryEntries() {
        FeatureRegistry registry = new FeatureRegistry();

        assertThrows(NullPointerException.class, () -> registry.registerAvailableFeature(null));
        assertThrows(NullPointerException.class, () -> registry.registerLoadedFeature("one", null));
    }
}
