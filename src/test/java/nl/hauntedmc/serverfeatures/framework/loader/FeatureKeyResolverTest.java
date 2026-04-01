package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureKeyResolverTest {

    @Test
    void resolveFeatureKeySupportsDirectCaseInsensitiveAliasAndClassNameMatching() {
        Map<String, FeatureDescriptor> available = new LinkedHashMap<>();
        available.put("ChatFilter", new FeatureDescriptor(
                "ChatFilter",
                "nl.hauntedmc.serverfeatures.features.chatfilter.ChatFilter",
                "Chat Filter",
                "1",
                Set.of(),
                Set.of()
        ));
        available.put("NightVision", new FeatureDescriptor(
                "NightVision",
                "nl.hauntedmc.serverfeatures.features.nightvision.NightVision",
                "Night Vision",
                "1",
                Set.of(),
                Set.of()
        ));

        Set<String> loaded = Set.of("LoadedFeature");

        assertEquals("ChatFilter", FeatureKeyResolver.resolveFeatureKey("ChatFilter", available, loaded, k -> null));
        assertEquals("ChatFilter", FeatureKeyResolver.resolveFeatureKey("chatfilter", available, loaded, k -> null));
        assertEquals("ChatFilter", FeatureKeyResolver.resolveFeatureKey("Chat Filter", available, loaded, k -> null));
        assertEquals("NightVision", FeatureKeyResolver.resolveFeatureKey("nightvision", available, loaded, k -> null));
        assertEquals("LoadedFeature", FeatureKeyResolver.resolveFeatureKey("loadedfeature", available, loaded, k -> null));
        assertEquals("LoadedFeature", FeatureKeyResolver.resolveFeatureKey("visible name", available, loaded, k -> "Visible Name"));
    }

    @Test
    void resolveFeatureKeyRejectsNullBlankAndUnknownInput() {
        assertNull(FeatureKeyResolver.resolveFeatureKey(null, Map.of(), Set.of(), k -> null));
        assertNull(FeatureKeyResolver.resolveFeatureKey("   ", Map.of(), Set.of(), k -> null));
        assertNull(FeatureKeyResolver.resolveFeatureKey("unknown", Map.of(), Set.of(), k -> null));
    }

    @Test
    void helperMethodsBehaveAsExpected() {
        assertEquals("X", FeatureKeyResolver.findCaseInsensitiveMatch("x", Set.of("X", "Y")));
        assertNull(FeatureKeyResolver.findCaseInsensitiveMatch("z", Set.of("X", "Y")));

        assertEquals("Name", FeatureKeyResolver.simpleClassName("a.b.Name"));
        assertEquals("Name", FeatureKeyResolver.simpleClassName("Name"));
        assertEquals("", FeatureKeyResolver.simpleClassName(" "));
        assertEquals("", FeatureKeyResolver.simpleClassName(null));

        assertTrue(FeatureKeyResolver.isValidFeatureKey("abc_123-XYZ"));
        assertFalse(FeatureKeyResolver.isValidFeatureKey("bad key"));
        assertFalse(FeatureKeyResolver.isValidFeatureKey("bad.key"));
        assertFalse(FeatureKeyResolver.isValidFeatureKey(""));
    }
}
