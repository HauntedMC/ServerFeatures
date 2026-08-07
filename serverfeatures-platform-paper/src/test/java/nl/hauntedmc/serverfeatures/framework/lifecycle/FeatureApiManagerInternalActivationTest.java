package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.framework.service.CapabilityRegistration;
import nl.hauntedmc.serverfeatures.framework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.service.InternalServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FeatureApiManagerInternalActivationTest {

    @Test
    void failedInternalActivationWithdrawsPublicCapabilityPublishedEarlier() {
        DefaultCapabilityRegistry publicRegistry = new DefaultCapabilityRegistry();
        InternalServiceRegistry internalRegistry = new InternalServiceRegistry();
        CapabilityRegistration conflict = internalRegistry.register(
                FeatureId.of("existing"),
                Runnable.class,
                mock(Runnable.class)
        );

        FeatureApiManager manager = new FeatureApiManager();
        manager.bindRegistry(publicRegistry, internalRegistry, "feature");
        manager.registerService(EconomyApi.class, mock(EconomyApi.class));
        manager.registerInternalService(Runnable.class, mock(Runnable.class));

        assertThrows(IllegalStateException.class, manager::activateServices);
        assertTrue(publicRegistry.reference(EconomyApi.class).get().isEmpty());
        assertFalse(manager.isActive());
        assertEquals(2, manager.getRegisteredServiceCount());

        conflict.close();
    }
}
