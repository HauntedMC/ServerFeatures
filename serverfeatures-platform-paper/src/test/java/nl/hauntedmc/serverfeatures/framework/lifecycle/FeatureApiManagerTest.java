package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceDirectory;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServiceCatalog;
import nl.hauntedmc.serverfeatures.test.TestFeatureServiceDirectory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureApiManagerTest {

    @Test
    void servicesAreStagedUntilActivationAndWithdrawnOnCleanup() {
        FeatureServiceDirectory directory = new TestFeatureServiceDirectory();
        FeatureApiManager manager = manager(directory, "Example");
        manager.registerService(String.class, "value");
        assertTrue(directory.find(String.class).isEmpty());
        assertFalse(manager.isActive());

        manager.activateServices();
        assertEquals("value", directory.find(String.class).orElseThrow());
        assertTrue(manager.isActive());

        manager.unregisterAllServices();
        assertTrue(directory.find(String.class).isEmpty());
        assertEquals(FeatureResourceState.CLOSED, manager.state());
    }

    @Test
    void differentOwnersCannotActivateSameApiType() {
        FeatureServiceDirectory directory = new TestFeatureServiceDirectory();
        FeatureApiManager first = manager(directory, "First");
        FeatureApiManager second = manager(directory, "Second");
        first.registerService(String.class, "first");
        second.registerService(String.class, "second");
        first.activateServices();
        assertThrows(IllegalStateException.class, second::activateServices);
        assertEquals("first", directory.find(String.class).orElseThrow());
    }

    @Test
    void registeringTheSameInstanceIsIdempotent() {
        FeatureServiceDirectory directory = new TestFeatureServiceDirectory();
        FeatureApiManager manager = manager(directory, "Example");
        Object service = new Object();
        manager.registerService(Object.class, service);
        manager.registerService(Object.class, service);
        assertSame(service, manager.findService(Object.class).orElseThrow());
        assertEquals(1, manager.getRegisteredServiceCount());
    }

    @Test
    void servicesRemainDiscoverableWithoutDataRegistryAfterActivation() {
        FeatureServiceCatalog catalog = new FeatureServiceCatalog();
        FeatureApiManager manager = new FeatureApiManager("Example", Optional::empty, catalog);
        manager.registerService(String.class, "local");
        assertTrue(catalog.find(String.class).isEmpty());
        manager.activateServices();
        assertEquals("local", catalog.find(String.class).orElseThrow());
        manager.unregisterAllServices();
        assertTrue(catalog.find(String.class).isEmpty());
    }

    @Test
    void activationHooksRunBeforePublication() {
        FeatureServiceCatalog catalog = new FeatureServiceCatalog();
        FeatureApiManager manager = new FeatureApiManager("Example", Optional::empty, catalog);
        boolean[] hookRan = {false};
        manager.registerActivationHook(() -> hookRan[0] = true);
        manager.registerService(String.class, "local");
        manager.activateServices();
        assertTrue(hookRan[0]);
        assertEquals("local", catalog.find(String.class).orElseThrow());
    }

    private static FeatureApiManager manager(FeatureServiceDirectory directory, String owner) {
        DataRegistryApi registry = mock(DataRegistryApi.class);
        when(registry.featureServices()).thenReturn(directory);
        return new FeatureApiManager(owner, () -> Optional.of(registry));
    }
}
