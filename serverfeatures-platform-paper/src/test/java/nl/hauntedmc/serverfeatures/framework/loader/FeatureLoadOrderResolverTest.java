package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureLoadOrderResolverTest {

    @Test
    void resolvesDependencyOrderForAcyclicGraph() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", descriptor("A", "B"));
        descriptors.put("B", descriptor("B", "C"));
        descriptors.put("C", descriptor("C"));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), logs);

        assertEquals(List.of("C", "B", "A"), result.loadOrder());
        assertTrue(result.skippedFeatures().isEmpty());
        assertTrue(logs.isEmpty());
    }

    @Test
    void sharedDependencyIsLoadedExactlyOnceBeforeAllDependents() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", descriptor("A", "Shared"));
        descriptors.put("B", descriptor("B", "Shared"));
        descriptors.put("Shared", descriptor("Shared"));

        FeatureLoadOrderResolver.Result result = resolve(descriptors, List.of("A", "B", "Shared"), new ArrayList<>());

        assertEquals(List.of("Shared", "A", "B"), result.loadOrder());
        assertEquals(1, result.loadOrder().stream().filter("Shared"::equals).count());
    }

    @Test
    void diamondGraphPreservesDependencyBeforeDependentInvariant() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("Root", descriptor("Root", "Left", "Right"));
        descriptors.put("Left", descriptor("Left", "Base"));
        descriptors.put("Right", descriptor("Right", "Base"));
        descriptors.put("Base", descriptor("Base"));

        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), new ArrayList<>());

        assertBefore(result.loadOrder(), "Base", "Left");
        assertBefore(result.loadOrder(), "Base", "Right");
        assertBefore(result.loadOrder(), "Left", "Root");
        assertBefore(result.loadOrder(), "Right", "Root");
        assertEquals(4, result.loadOrder().size());
    }

    @Test
    void marksFeatureSkippedWhenDependencyMissing() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", descriptor("A", "Missing"));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), logs);

        assertTrue(result.loadOrder().isEmpty());
        assertEquals(Set.of("A"), result.skippedFeatures());
        assertTrue(logs.stream().anyMatch(msg -> msg.contains("dependency 'Missing' is unavailable")));
    }

    @Test
    void missingTransitiveDependencySkipsEntireDependentChainButNotIndependentFeatures() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("Root", descriptor("Root", "Middle"));
        descriptors.put("Middle", descriptor("Middle", "Missing"));
        descriptors.put("Independent", descriptor("Independent"));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), logs);

        assertEquals(List.of("Independent"), result.loadOrder());
        assertEquals(Set.of("Root", "Middle"), result.skippedFeatures());
        assertTrue(logs.stream().anyMatch(msg -> msg.contains("dependency 'Middle' failed")));
    }

    @Test
    void unavailableRequestedRootIsSkippedAndLogged() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        List<String> logs = new ArrayList<>();

        FeatureLoadOrderResolver.Result result = resolve(descriptors, List.of("Unknown"), logs);

        assertTrue(result.loadOrder().isEmpty());
        assertEquals(Set.of("Unknown"), result.skippedFeatures());
        assertEquals(List.of("Feature 'Unknown' is not registered as available."), logs);
    }

    @Test
    void detectsCyclesAndSkipsInvolvedFeatures() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", descriptor("A", "B"));
        descriptors.put("B", descriptor("B", "A"));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), logs);

        assertTrue(result.loadOrder().isEmpty());
        assertEquals(Set.of("A", "B"), result.skippedFeatures());
        assertTrue(logs.stream().anyMatch(msg -> msg.contains("Dependency cycle detected: A -> B -> A")));
    }

    @Test
    void cycleFailureDoesNotPoisonIndependentFeature() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", descriptor("A", "B"));
        descriptors.put("B", descriptor("B", "A"));
        descriptors.put("Healthy", descriptor("Healthy"));

        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), new ArrayList<>());

        assertEquals(List.of("Healthy"), result.loadOrder());
        assertEquals(Set.of("A", "B"), result.skippedFeatures());
    }

    @Test
    void dependentOnCycleIsSkippedWhileUnrelatedDependencyStillLoads() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("Root", descriptor("Root", "A", "HealthyDependency"));
        descriptors.put("A", descriptor("A", "B"));
        descriptors.put("B", descriptor("B", "A"));
        descriptors.put("HealthyDependency", descriptor("HealthyDependency"));

        FeatureLoadOrderResolver.Result result = resolve(descriptors, descriptors.keySet(), new ArrayList<>());

        assertTrue(result.loadOrder().contains("HealthyDependency"));
        assertFalse(result.loadOrder().contains("Root"));
        assertEquals(Set.of("Root", "A", "B"), result.skippedFeatures());
    }

    @Test
    void duplicateRequestedFeatureNamesDoNotDuplicateLoadEntries() {
        Map<String, FeatureDescriptor> descriptors = Map.of("A", descriptor("A"));

        FeatureLoadOrderResolver.Result result = resolve(descriptors, List.of("A", "A", "A"), new ArrayList<>());

        assertEquals(List.of("A"), result.loadOrder());
        assertTrue(result.skippedFeatures().isEmpty());
    }

    private static FeatureLoadOrderResolver.Result resolve(
            Map<String, FeatureDescriptor> descriptors,
            Iterable<String> requested,
            List<String> logs
    ) {
        List<String> featureNames = new ArrayList<>();
        requested.forEach(featureNames::add);
        return FeatureLoadOrderResolver.resolveLoadOrder(
                featureNames,
                descriptors::get,
                name -> descriptors.containsKey(name) ? name : null,
                logs::add
        );
    }

    private static FeatureDescriptor descriptor(String name, String... dependencies) {
        return new FeatureDescriptor(name, "x." + name, name, "1", Set.of(dependencies), Set.of());
    }

    private static void assertBefore(List<String> order, String dependency, String dependent) {
        assertTrue(order.indexOf(dependency) >= 0, () -> dependency + " was not loaded");
        assertTrue(order.indexOf(dependent) >= 0, () -> dependent + " was not loaded");
        assertTrue(order.indexOf(dependency) < order.indexOf(dependent),
                () -> dependency + " must load before " + dependent + ": " + order);
    }
}
