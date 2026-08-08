package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInFeaturesTest {

    @Test
    void inventoryContainsEveryCurrentBuiltInExactlyOnce() {
        var definitions = BuiltInFeatures.definitions();
        assertEquals(64, definitions.size());

        Set<String> names = new HashSet<>();
        Set<String> implementations = new HashSet<>();
        definitions.forEach(definition -> {
            assertTrue(names.add(definition.registryName().toLowerCase(Locale.ROOT)),
                    () -> "Duplicate feature name: " + definition.registryName());
            assertTrue(implementations.add(definition.implementationClassName()),
                    () -> "Duplicate implementation: " + definition.implementationClassName());
            assertNotNull(definition.createMeta());
        });
    }

    @Test
    void everyImplementationResolvesBackToItsManifestDefinition() {
        BuiltInFeatures.definitions().forEach(definition -> assertSame(
                definition,
                BuiltInFeatures.findByImplementationClassName(definition.implementationClassName()).orElseThrow()
        ));
        assertTrue(BuiltInFeatures.findByImplementationClassName("not.a.real.Feature").isEmpty());
    }

    @Test
    void discoveryMetadataDoesNotStoreImplementationClassHandles() {
        assertFalse(Arrays.stream(BuiltInFeatures.Definition.class.getRecordComponents())
                .anyMatch(component -> component.getType() == Class.class));
        BuiltInFeatures.definitions().forEach(definition -> assertEquals(
                definition.implementationClassName(),
                definition.implementationType().getName()
        ));
    }
}
