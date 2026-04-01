package nl.hauntedmc.serverfeatures.framework.loader.dependency;

import nl.hauntedmc.serverfeatures.framework.loader.FeatureDescriptor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureDependencyTraversalTest {

    @Test
    void checkDependenciesLoadsMissingDependenciesInOrder() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("B"), Set.of()));
        descriptors.put("B", new FeatureDescriptor("B", "x.B", "B", "1", Set.of("C"), Set.of()));
        descriptors.put("C", new FeatureDescriptor("C", "x.C", "C", "1", Set.of(), Set.of()));

        Set<String> loaded = new LinkedHashSet<>();
        List<String> infos = new ArrayList<>();
        AtomicInteger loadCalls = new AtomicInteger();

        boolean ok = FeatureDependencyTraversal.checkDependencies(
                "A",
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                loaded::contains,
                descriptors::get,
                dep -> dep,
                dep -> {
                    loadCalls.incrementAndGet();
                    loaded.add(dep);
                    return true;
                },
                msg -> {},
                infos::add
        );

        assertTrue(ok);
        assertEquals(2, loadCalls.get());
        assertEquals(Set.of("B", "C"), loaded);
        assertTrue(infos.stream().anyMatch(s -> s.contains("Enabling dependency")));
    }

    @Test
    void checkDependenciesFailsForMissingDependencyAndCycles() {
        Map<String, FeatureDescriptor> missing = Map.of(
                "A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("Missing"), Set.of())
        );
        List<String> warnings = new ArrayList<>();

        boolean missingOk = FeatureDependencyTraversal.checkDependencies(
                "A",
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                name -> false,
                missing::get,
                dep -> null,
                dep -> true,
                warnings::add,
                msg -> {}
        );
        assertFalse(missingOk);
        assertTrue(warnings.stream().anyMatch(s -> s.contains("Missing dependency")));

        Map<String, FeatureDescriptor> cycle = Map.of(
                "A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("B"), Set.of()),
                "B", new FeatureDescriptor("B", "x.B", "B", "1", Set.of("A"), Set.of())
        );
        warnings.clear();

        boolean cycleOk = FeatureDependencyTraversal.checkDependencies(
                "A",
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                name -> false,
                cycle::get,
                dep -> dep,
                dep -> true,
                warnings::add,
                msg -> {}
        );

        assertFalse(cycleOk);
        assertTrue(warnings.stream().anyMatch(s -> s.contains("Circular dependency detected")));
    }
}
