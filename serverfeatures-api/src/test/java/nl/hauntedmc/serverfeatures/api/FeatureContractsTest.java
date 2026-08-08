package nl.hauntedmc.serverfeatures.api;

import nl.hauntedmc.serverfeatures.api.feature.FeatureClassification;
import nl.hauntedmc.serverfeatures.api.feature.FeatureDescriptor;
import nl.hauntedmc.serverfeatures.api.feature.FeatureFailure;
import nl.hauntedmc.serverfeatures.api.feature.FeatureId;
import nl.hauntedmc.serverfeatures.api.feature.FeatureRole;
import nl.hauntedmc.serverfeatures.api.feature.FeatureSnapshot;
import nl.hauntedmc.serverfeatures.api.feature.FeatureState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureContractsTest {

    @Test
    void descriptorNormalizesAndDefensivelyCopiesMetadata() {
        FeatureId id = FeatureId.of("lottery");
        Set<FeatureId> dependencies = new LinkedHashSet<>(Set.of(FeatureId.of("economy")));
        Set<String> capabilities = new LinkedHashSet<>(Set.of("serverfeatures:lottery"));
        Set<FeatureRole> roles = new LinkedHashSet<>(Set.of(FeatureRole.CAPABILITY_CONSUMER));

        FeatureDescriptor descriptor = new FeatureDescriptor(
                id,
                " Lottery ",
                " 3.3.0 ",
                FeatureClassification.CAPABILITY_CONSUMER,
                dependencies,
                capabilities,
                roles
        );
        dependencies.clear();
        capabilities.clear();
        roles.clear();

        assertEquals("Lottery", descriptor.displayName());
        assertEquals("3.3.0", descriptor.version());
        assertEquals(Set.of(FeatureId.of("economy")), descriptor.requiredFeatures());
        assertEquals(Set.of("serverfeatures:lottery"), descriptor.providedCapabilities());
        assertEquals(Set.of(FeatureRole.CAPABILITY_CONSUMER), descriptor.roles());
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureDescriptor(id, " ", "1", FeatureClassification.INTERNAL, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureDescriptor(id, "x", " ", FeatureClassification.INTERNAL, null, null, null));
    }

    @Test
    void snapshotCapturesImmutableFailureAndGenerationState() {
        FeatureDescriptor descriptor = new FeatureDescriptor(
                FeatureId.of("economy"),
                "Economy",
                "3.3.0",
                FeatureClassification.CAPABILITY_PROVIDER,
                Set.of(),
                Set.of("serverfeatures:economy"),
                Set.of(FeatureRole.CAPABILITY_PROVIDER)
        );
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        FeatureFailure failure = new FeatureFailure(" startup ", " init ", Optional.of(" boom "));
        Set<FeatureId> missing = new LinkedHashSet<>(Set.of(FeatureId.of("dependency")));

        FeatureSnapshot snapshot = new FeatureSnapshot(
                descriptor,
                true,
                FeatureState.FAILED,
                Optional.of("boom"),
                Optional.of(failure),
                missing,
                now,
                Optional.empty(),
                4L,
                now.plusSeconds(1)
        );
        missing.clear();

        assertEquals(Set.of(FeatureId.of("dependency")), snapshot.unavailableDependencies());
        assertEquals(4L, snapshot.generation());
        assertEquals("startup", failure.phase());
        assertEquals("init", failure.code());
        assertEquals(Optional.of("boom"), failure.message());
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureSnapshot(descriptor, true, FeatureState.ACTIVE, Optional.empty(), Optional.empty(),
                        Set.of(), now, Optional.empty(), -1L, now));
        assertTrue(snapshot.observedAt().isAfter(snapshot.lastTransitionAt()));
    }
}
