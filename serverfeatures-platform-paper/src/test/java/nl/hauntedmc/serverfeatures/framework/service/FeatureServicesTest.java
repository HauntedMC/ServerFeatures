package nl.hauntedmc.serverfeatures.framework.service;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureServicesTest {

    @Test
    void fallsBackToInProcessCatalogWhenDataRegistryIsUnavailable() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        FeatureLifecycleFactory lifecycleFactory = mock(FeatureLifecycleFactory.class);
        when(plugin.getDataRegistry()).thenReturn(Optional.empty());
        when(plugin.getFeatureLifecycleFactory()).thenReturn(lifecycleFactory);
        when(lifecycleFactory.findService(String.class)).thenReturn(Optional.of("local"));

        assertEquals("local", FeatureServices.find(plugin, String.class).orElseThrow());
    }
}
