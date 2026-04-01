package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeatureConfigSchemaTest {

    @Test
    void topLevelKeysFromDefaultsCollapsesNestedPaths() {
        ConfigMap defaults = new ConfigMap()
                .put("enabled", true)
                .put("settings.delay", 5)
                .put("settings.mode", "fast")
                .put("title", "x");

        assertEquals(Set.of("enabled", "settings", "title"), FeatureConfigSchema.topLevelKeysFromDefaults(defaults));
    }

    @Test
    void classifyDetectsSupportedKinds() {
        assertEquals(FeatureConfigSchema.Kind.MAP, FeatureConfigSchema.classify(Map.of("a", 1)));
        assertEquals(FeatureConfigSchema.Kind.LIST, FeatureConfigSchema.classify(List.of(1)));
        assertEquals(FeatureConfigSchema.Kind.BOOLEAN, FeatureConfigSchema.classify(true));
        assertEquals(FeatureConfigSchema.Kind.NUMBER, FeatureConfigSchema.classify(1));
        assertEquals(FeatureConfigSchema.Kind.STRING, FeatureConfigSchema.classify("x"));
        assertNull(FeatureConfigSchema.classify(null));
    }

    @Test
    void expectedKindForTopKeyUsesDirectAndNestedDefaults() {
        ConfigMap defaults = new ConfigMap()
                .put("plain", 1)
                .put("nested.child", "x");

        assertEquals(FeatureConfigSchema.Kind.NUMBER, FeatureConfigSchema.expectedKindForTopKey("plain", defaults));
        assertEquals(FeatureConfigSchema.Kind.MAP, FeatureConfigSchema.expectedKindForTopKey("nested", defaults));
        assertNull(FeatureConfigSchema.expectedKindForTopKey("missing", defaults));
    }
}
