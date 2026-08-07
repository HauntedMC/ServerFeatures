package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInFeaturesTest {

    @Test
    void inventoryContainsEveryCurrentBuiltInExactlyOnce() {
        var definitions = BuiltInFeatures.definitions();
        assertEquals(64, definitions.size());

        Set<String> names = new HashSet<>();
        Set<Class<?>> implementations = new HashSet<>();
        definitions.forEach(definition -> {
            assertTrue(names.add(definition.registryName().toLowerCase(Locale.ROOT)),
                    () -> "Duplicate feature name: " + definition.registryName());
            assertTrue(implementations.add(definition.implementationType()),
                    () -> "Duplicate implementation: " + definition.implementationType().getName());
            assertNotNull(definition.createMeta());
        });
    }
}
