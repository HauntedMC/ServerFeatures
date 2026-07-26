package nl.hauntedmc.serverfeatures.features;

import io.github.classgraph.ClassGraph;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FeatureDefaultsContractTest {

    @Test
    void everyDiscoveredFeatureHasSafeScopedDefaults() throws Exception {
        FeatureContext<?> context = mock(FeatureContext.class);
        Map<String, String> messageRootOwners = new LinkedHashMap<>();
        int discovered = 0;
        try (var scan = new ClassGraph()
                .enableClassInfo()
                .acceptPackages("nl.hauntedmc.serverfeatures.features")
                .scan()) {
            for (var classInfo : scan.getSubclasses(BukkitBaseFeature.class.getName())) {
                if (classInfo.isAbstract()) {
                    continue;
                }
                discovered++;
                Class<?> featureClass = classInfo.loadClass();
                Object instance = featureClass.getDeclaredConstructor(FeatureContext.class).newInstance(context);
                assertTrue(instance instanceof BukkitBaseFeature<?>, featureClass.getName());
                BukkitBaseFeature<?> feature = (BukkitBaseFeature<?>) instance;

                ConfigMap config = feature.getDefaultConfig();
                assertNotNull(config, featureClass.getName() + " returned null config defaults");
                assertTrue(config.contains("enabled"), featureClass.getName() + " has no enabled default");
                config.entrySet().forEach(entry -> {
                    assertFalse(entry.getKey().isBlank(), featureClass.getName() + " has a blank config key");
                    assertNotNull(entry.getValue(), featureClass.getName() + " has a null config default");
                });

                MessageMap messages = feature.getDefaultMessages();
                assertNotNull(messages, featureClass.getName() + " returned null message defaults");
                validateMeta(featureClass);
                messages.getMessages().forEach((key, value) -> {
                    int separator = key.indexOf('.');
                    assertTrue(separator > 0, featureClass.getName() + " has an unscoped message key: " + key);
                    String root = key.substring(0, separator);
                    String previousOwner = messageRootOwners.putIfAbsent(root, featureClass.getName());
                    assertTrue(
                            previousOwner == null || previousOwner.equals(featureClass.getName()),
                            "Message root '" + root + "' is shared by " + previousOwner
                                    + " and " + featureClass.getName()
                    );
                    assertNotNull(value, featureClass.getName() + " has a null message default");
                });
            }
        }
        assertTrue(discovered >= 50, "Feature discovery unexpectedly found only " + discovered + " features");
    }

    private static void validateMeta(Class<?> featureClass) throws Exception {
        Class<?> metaClass = Class.forName(featureClass.getPackageName() + ".meta.Meta");
        Object instance = metaClass.getDeclaredConstructor().newInstance();
        assertTrue(instance instanceof BaseMeta, metaClass.getName());
        String featureName = ((BaseMeta) instance).getFeatureName();
        assertNotNull(featureName, metaClass.getName() + " returned a null feature name");
        assertFalse(featureName.isBlank(), metaClass.getName() + " returned a blank feature name");
    }
}
