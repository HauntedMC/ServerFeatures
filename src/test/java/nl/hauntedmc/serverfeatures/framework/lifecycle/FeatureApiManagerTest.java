package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.backend.service.DefaultFeatureServiceDirectory;
import nl.hauntedmc.serverfeatures.api.APIRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureApiManagerTest {

    @AfterEach
    void cleanup() {
        APIRegistry.clear();
    }

    @Test
    void registerAndCleanupManageLocalAndDataRegistryServices() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        when(dataRegistry.featureServices()).thenReturn(directory);
        FeatureApiManager manager = new FeatureApiManager("Example", () -> Optional.of(dataRegistry));

        manager.registerService(String.class, "value");

        assertEquals("value", APIRegistry.get(String.class).orElseThrow());
        assertEquals("value", directory.find(String.class).orElseThrow());
        assertEquals("ServerFeatures", directory.describe(String.class).orElseThrow().ownerPlugin());

        manager.unregisterAllServices();

        assertTrue(APIRegistry.get(String.class).isEmpty());
        assertTrue(directory.find(String.class).isEmpty());
    }

    @Test
    void replacementDoesNotLeaveOldDataRegistryHandle() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        when(dataRegistry.featureServices()).thenReturn(directory);
        FeatureApiManager manager = new FeatureApiManager("Example", () -> Optional.of(dataRegistry));

        manager.registerService(String.class, "first");
        manager.registerService(String.class, "second");

        assertEquals("second", directory.find(String.class).orElseThrow());

        manager.unregisterService(String.class);

        assertTrue(directory.find(String.class).isEmpty());
    }
}
