package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.framework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.service.InternalServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FeatureApiManagerTest {

    @Test
    void servicesAreStagedUntilActivationAndWithdrawnOnCleanup() {
        DefaultCapabilityRegistry publicRegistry = new DefaultCapabilityRegistry();
        InternalServiceRegistry internalRegistry = new InternalServiceRegistry();
        FeatureApiManager manager = manager(publicRegistry, internalRegistry, "Example");
        EconomyApi economy = mock(EconomyApi.class);
        Runnable runtime = () -> { };

        manager.registerService(EconomyApi.class, economy);
        manager.registerInternalService(Runnable.class, runtime);
        assertTrue(publicRegistry.reference(EconomyApi.class).get().isEmpty());
        assertTrue(internalRegistry.find(Runnable.class).isEmpty());
        assertFalse(manager.isActive());

        manager.activateServices();
        assertTrue(publicRegistry.reference(EconomyApi.class).get().isPresent());
        assertSame(runtime, internalRegistry.require(Runnable.class));
        assertTrue(manager.isActive());

        manager.unregisterAllServices();
        assertTrue(publicRegistry.reference(EconomyApi.class).get().isEmpty());
        assertTrue(internalRegistry.find(Runnable.class).isEmpty());
        assertEquals(FeatureResourceState.CLOSED, manager.state());
    }

    @Test
    void differentOwnersCannotActivateSamePublicCapability() {
        DefaultCapabilityRegistry publicRegistry = new DefaultCapabilityRegistry();
        InternalServiceRegistry internalRegistry = new InternalServiceRegistry();
        FeatureApiManager first = manager(publicRegistry, internalRegistry, "First");
        FeatureApiManager second = manager(publicRegistry, internalRegistry, "Second");
        first.registerService(EconomyApi.class, mock(EconomyApi.class));
        second.registerService(EconomyApi.class, mock(EconomyApi.class));
        first.activateServices();

        assertThrows(IllegalStateException.class, second::activateServices);
        assertTrue(publicRegistry.reference(EconomyApi.class).get().isPresent());
    }

    @Test
    void registeringSameInstanceIsIdempotent() {
        DefaultCapabilityRegistry publicRegistry = new DefaultCapabilityRegistry();
        InternalServiceRegistry internalRegistry = new InternalServiceRegistry();
        FeatureApiManager manager = manager(publicRegistry, internalRegistry, "Example");
        EconomyApi economy = mock(EconomyApi.class);
        manager.registerService(EconomyApi.class, economy);
        manager.registerService(EconomyApi.class, economy);
        assertEquals(1, manager.getRegisteredServiceCount());
    }

    @Test
    void activeOwnerCanReplaceServiceWithoutAvailabilityGap() {
        DefaultCapabilityRegistry publicRegistry = new DefaultCapabilityRegistry();
        InternalServiceRegistry internalRegistry = new InternalServiceRegistry();
        FeatureApiManager manager = manager(publicRegistry, internalRegistry, "Example");
        EconomyApi first = mock(EconomyApi.class);
        EconomyApi second = mock(EconomyApi.class);
        manager.registerService(EconomyApi.class, first);
        manager.activateServices();
        long generation = publicRegistry.reference(EconomyApi.class).generation().orElseThrow();

        manager.registerService(EconomyApi.class, second);

        assertTrue(publicRegistry.reference(EconomyApi.class).get().isPresent());
        assertTrue(publicRegistry.reference(EconomyApi.class).generation().orElseThrow() > generation);
    }

    @Test
    void activationHooksRunBeforePublication() {
        DefaultCapabilityRegistry publicRegistry = new DefaultCapabilityRegistry();
        InternalServiceRegistry internalRegistry = new InternalServiceRegistry();
        FeatureApiManager manager = manager(publicRegistry, internalRegistry, "Example");
        boolean[] hookRan = {false};
        manager.registerActivationHook(() -> {
            assertTrue(publicRegistry.reference(EconomyApi.class).get().isEmpty());
            hookRan[0] = true;
        });
        manager.registerService(EconomyApi.class, mock(EconomyApi.class));
        manager.activateServices();

        assertTrue(hookRan[0]);
        assertTrue(publicRegistry.reference(EconomyApi.class).get().isPresent());
    }

    @Test
    void managerMustBeBoundBeforeRegistration() {
        FeatureApiManager manager = new FeatureApiManager();
        assertThrows(
                IllegalStateException.class,
                () -> manager.registerInternalService(Runnable.class, (Runnable) () -> { })
        );
    }

    private static FeatureApiManager manager(
            DefaultCapabilityRegistry publicRegistry,
            InternalServiceRegistry internalRegistry,
            String owner
    ) {
        FeatureApiManager manager = new FeatureApiManager();
        manager.bindRegistry(publicRegistry, internalRegistry, owner);
        return manager;
    }
}
