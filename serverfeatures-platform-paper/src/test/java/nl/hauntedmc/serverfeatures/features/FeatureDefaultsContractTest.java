package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.service.CapabilityListener;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRef;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRegistry;
import nl.hauntedmc.serverfeatures.framework.loader.BuiltInFeatures;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureDefaultsContractTest {

    @Test
    void everyBuiltInFeatureHasSafeScopedDefaults() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.capabilities()).thenReturn(new EmptyCapabilityRegistry());

        FeatureContext<?> context = mock(FeatureContext.class);
        when(context.plugin()).thenReturn(plugin);

        Map<String, String> messageRootOwners = new LinkedHashMap<>();
        int discovered = 0;
        for (BuiltInFeatures.Definition definition : BuiltInFeatures.definitions()) {
            discovered++;
            BukkitBaseFeature<?> feature = definition.createFeature(context);
            String featureClassName = definition.implementationType().getName();

            ConfigMap config = feature.getDefaultConfig();
            assertNotNull(config, featureClassName + " returned null config defaults");
            assertTrue(config.contains("enabled"), featureClassName + " has no enabled default");
            config.entrySet().forEach(entry -> {
                assertFalse(entry.getKey().isBlank(), featureClassName + " has a blank config key");
                assertNotNull(entry.getValue(), featureClassName + " has a null config default");
            });

            MessageMap messages = feature.getDefaultMessages();
            assertNotNull(messages, featureClassName + " returned null message defaults");
            validateMeta(definition);
            messages.getMessages().forEach((key, value) -> {
                int separator = key.indexOf('.');
                assertTrue(separator > 0, featureClassName + " has an unscoped message key: " + key);
                String root = key.substring(0, separator);
                String previousOwner = messageRootOwners.putIfAbsent(root, featureClassName);
                assertTrue(
                        previousOwner == null || previousOwner.equals(featureClassName),
                        "Message root '" + root + "' is shared by " + previousOwner
                                + " and " + featureClassName
                );
                assertNotNull(value, featureClassName + " has a null message default");
            });
        }
        assertEquals(64, discovered, "Built-in manifest size changed unexpectedly");
    }

    private static void validateMeta(BuiltInFeatures.Definition definition) {
        BaseMeta meta = definition.createMeta();
        String featureClassName = definition.implementationType().getName();
        assertNotNull(meta, featureClassName + " returned null metadata");
        String featureName = meta.getFeatureName();
        assertNotNull(featureName, featureClassName + " returned a null feature name");
        assertFalse(featureName.isBlank(), featureClassName + " returned a blank feature name");
    }

    private static final class EmptyCapabilityRegistry implements CapabilityRegistry {
        @Override
        public <T> CapabilityRef<T> reference(Class<T> type) {
            return new EmptyCapabilityRef<>(type);
        }

        @Override
        public Set<Class<?>> availableTypes() {
            return Set.of();
        }

        @Override
        public AutoCloseable subscribe(CapabilityListener listener) {
            return () -> { };
        }
    }

    private record EmptyCapabilityRef<T>(Class<T> type) implements CapabilityRef<T> {
        @Override
        public Optional<T> get() {
            return Optional.empty();
        }
    }
}
