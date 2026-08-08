package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.framework.loader.BuiltInFeatures;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FeatureFactoryTest {

    @Test
    void optionalRuntimeLinkageFailureDoesNotCrashFramework() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("feature-factory-test"));
        FeatureContext<?> context = mock(FeatureContext.class);
        when(context.plugin()).thenReturn(plugin);
        BuiltInFeatures.Definition definition = mock(BuiltInFeatures.Definition.class);
        when(definition.createFeature(context))
                .thenThrow(new NoClassDefFoundError("optional/plugin/Api"));

        try (MockedStatic<BuiltInFeatures> manifest = mockStatic(BuiltInFeatures.class)) {
            manifest.when(() -> BuiltInFeatures.findByImplementationClassName("example.OptionalFeature"))
                    .thenReturn(Optional.of(definition));

            assertNull(FeatureFactory.createFeature("example.OptionalFeature", context));
        }
    }
}
