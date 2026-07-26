package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.serverfeatures.test.TestFeatureServiceDirectory;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServiceCatalog;
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
    void registerAndCleanupManagePublicApiServices() {
        FeatureServiceDirectory directory = new TestFeatureServiceDirectory();
        FeatureApiManager manager = manager(directory, "Example");
        manager.registerService(String.class, "value");
        assertEquals("value", directory.find(String.class).orElseThrow());
        manager.unregisterAllServices();
        assertTrue(directory.find(String.class).isEmpty());
    }

    @Test
    void differentOwnersCannotPublishSameApiType() {
        FeatureServiceDirectory directory = new TestFeatureServiceDirectory();
        FeatureApiManager first = manager(directory, "First");
        FeatureApiManager second = manager(directory, "Second");
        first.registerService(String.class, "first");
        assertThrows(IllegalStateException.class, () -> second.registerService(String.class, "second"));
        assertEquals("first", directory.find(String.class).orElseThrow());
    }

    @Test
    void registeringTheSameInstanceIsIdempotent() {
        FeatureServiceDirectory directory = new TestFeatureServiceDirectory();
        FeatureApiManager manager = manager(directory, "Example");
        Object service = new Object();
        manager.registerService(Object.class, service);
        manager.registerService(Object.class, service);
        assertSame(service, directory.find(Object.class).orElseThrow());
        assertEquals(1, manager.getRegisteredServiceCount());
    }

    @Test
    void servicesRemainDiscoverableWithoutDataRegistry() {
        FeatureServiceCatalog catalog = new FeatureServiceCatalog();
        FeatureApiManager manager = new FeatureApiManager("Example", Optional::empty, catalog);
        manager.registerService(String.class, "local");

        assertEquals("local", catalog.find(String.class).orElseThrow());
        assertEquals("local", manager.findService(String.class).orElseThrow());

        manager.unregisterAllServices();
        assertTrue(catalog.find(String.class).isEmpty());
    }

    @Test
    void localCatalogPreventsCollisionsWithoutDataRegistry() {
        FeatureServiceCatalog catalog = new FeatureServiceCatalog();
        FeatureApiManager first = new FeatureApiManager("First", Optional::empty, catalog);
        FeatureApiManager second = new FeatureApiManager("Second", Optional::empty, catalog);
        first.registerService(String.class, "first");

        assertThrows(IllegalStateException.class, () -> second.registerService(String.class, "second"));
        assertEquals("first", catalog.find(String.class).orElseThrow());
    }

    private static FeatureApiManager manager(FeatureServiceDirectory directory, String owner) {
        DataRegistryApi registry = mock(DataRegistryApi.class);
        when(registry.featureServices()).thenReturn(directory);
        return new FeatureApiManager(owner, () -> Optional.of(registry));
    }
}
