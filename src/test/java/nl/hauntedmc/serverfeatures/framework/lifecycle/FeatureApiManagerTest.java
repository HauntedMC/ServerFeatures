package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.dataregistry.backend.service.DefaultFeatureServiceDirectory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureApiManagerTest {

    @Test
    void registerAndCleanupManageDataRegistryServices() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        when(dataRegistry.featureServices()).thenReturn(directory);
        FeatureApiManager manager = new FeatureApiManager("Example", () -> Optional.of(dataRegistry));

        manager.registerService(String.class, "value");

        assertEquals("value", directory.find(String.class).orElseThrow());
        assertEquals("ServerFeatures", directory.describe(String.class).orElseThrow().ownerPlugin());

        manager.unregisterAllServices();

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

    @Test
    void nullDataRegistrySupplierResultSkipsCatalogPublication() {
        FeatureApiManager manager = new FeatureApiManager("Example", () -> null);

        manager.registerService(String.class, "value");

        assertEquals(1, manager.getRegisteredServiceCount());
    }

    @Test
    void differentOwnersCannotPublishSameApiType() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        when(dataRegistry.featureServices()).thenReturn(directory);
        FeatureApiManager first = new FeatureApiManager("First", () -> Optional.of(dataRegistry));
        FeatureApiManager second = new FeatureApiManager("Second", () -> Optional.of(dataRegistry));

        first.registerService(String.class, "first");

        assertThrows(IllegalStateException.class, () -> second.registerService(String.class, "second"));
        assertEquals(0, second.getRegisteredServiceCount());
        assertEquals("first", directory.find(String.class).orElseThrow());

        first.unregisterAllServices();
        assertTrue(directory.find(String.class).isEmpty());
    }

    @Test
    void registeringSameInstanceIsIdempotent() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        FeatureServiceDirectory directory = new DefaultFeatureServiceDirectory();
        when(dataRegistry.featureServices()).thenReturn(directory);
        FeatureApiManager manager = new FeatureApiManager("Example", () -> Optional.of(dataRegistry));
        Object service = new Object();

        manager.registerService(Object.class, service);
        manager.registerService(Object.class, service);

        assertSame(service, directory.find(Object.class).orElseThrow());
        assertEquals(1, manager.getRegisteredServiceCount());

        manager.unregisterService(Object.class);

        assertTrue(directory.find(Object.class).isEmpty());
    }
}
