package nl.hauntedmc.serverfeatures.api;

import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.api.service.CapabilityRef;
import nl.hauntedmc.serverfeatures.api.service.CapabilityUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityAndServiceContractsTest {

    @Test
    void versionsAreNormalizedAndCurrentFactoryKeepsTheApiVersion() {
        ServerFeaturesApiVersion version = new ServerFeaturesApiVersion(" 3.3.0 ", " build-12 ");

        assertEquals("3.3.0", version.apiVersion());
        assertEquals("build-12", version.implementationVersion());
        assertEquals(ServerFeaturesApiVersion.CURRENT,
                ServerFeaturesApiVersion.current("runtime").apiVersion());
        assertThrows(NullPointerException.class, () -> new ServerFeaturesApiVersion(null, "runtime"));
        assertThrows(IllegalArgumentException.class, () -> new ServerFeaturesApiVersion(" ", "runtime"));
        assertThrows(IllegalArgumentException.class, () -> new ServerFeaturesApiVersion("3.3.0", " "));
    }

    @Test
    void featureIdsAreStableNormalizedComparableValues() {
        FeatureId id = FeatureId.of(" CombatTag.Main_1 ");

        assertEquals("combattag.main_1", id.value());
        assertEquals("combattag.main_1", id.toString());
        assertTrue(id.compareTo(FeatureId.of("vanish")) < 0);
        assertThrows(NullPointerException.class, () -> id.compareTo(null));
        assertThrows(NullPointerException.class, () -> FeatureId.of(null));
        assertThrows(IllegalArgumentException.class, () -> FeatureId.of(""));
        assertThrows(IllegalArgumentException.class, () -> FeatureId.of("-combat"));
        assertThrows(IllegalArgumentException.class, () -> FeatureId.of("combat/tag"));
    }

    @Test
    void capabilityReferencesRemainStableAcrossAvailabilityChanges() {
        AtomicReference<Runnable> current = new AtomicReference<>();
        CapabilityRef<Runnable> reference = new CapabilityRef<>() {
            @Override
            public Class<Runnable> type() {
                return Runnable.class;
            }

            @Override
            public Optional<Runnable> get() {
                return Optional.ofNullable(current.get());
            }
        };

        assertFalse(reference.isAvailable());
        assertTrue(reference.generation().isEmpty());
        CapabilityUnavailableException unavailable = assertThrows(
                CapabilityUnavailableException.class,
                reference::require
        );
        assertEquals(Runnable.class, unavailable.capabilityType());
        assertTrue(unavailable.getMessage().contains(Runnable.class.getName()));

        Runnable provider = () -> { };
        current.set(provider);
        assertTrue(reference.isAvailable());
        assertEquals(1L, reference.generation().orElseThrow());
        assertSame(provider, reference.require());
        assertThrows(NullPointerException.class, () -> new CapabilityUnavailableException(null));
    }
}
