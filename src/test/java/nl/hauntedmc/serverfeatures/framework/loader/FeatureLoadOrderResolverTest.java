package nl.hauntedmc.serverfeatures.framework.loader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureLoadOrderResolverTest {

    @Test
    void resolvesDependencyOrderForAcyclicGraph() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("B"), Set.of()));
        descriptors.put("B", new FeatureDescriptor("B", "x.B", "B", "1", Set.of("C"), Set.of()));
        descriptors.put("C", new FeatureDescriptor("C", "x.C", "C", "1", Set.of(), Set.of()));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = FeatureLoadOrderResolver.resolveLoadOrder(
                descriptors.keySet(),
                descriptors::get,
                name -> descriptors.containsKey(name) ? name : null,
                logs::add
        );

        assertEquals(List.of("C", "B", "A"), result.loadOrder());
        assertTrue(result.skippedFeatures().isEmpty());
        assertTrue(logs.isEmpty());
    }

    @Test
    void marksFeatureSkippedWhenDependencyMissing() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("Missing"), Set.of()));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = FeatureLoadOrderResolver.resolveLoadOrder(
                descriptors.keySet(),
                descriptors::get,
                name -> descriptors.containsKey(name) ? name : null,
                logs::add
        );

        assertTrue(result.loadOrder().isEmpty());
        assertEquals(Set.of("A"), result.skippedFeatures());
        assertTrue(logs.stream().anyMatch(msg -> msg.contains("cannot be loaded")));
    }

    @Test
    void detectsCyclesAndSkipsInvolvedFeatures() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("B"), Set.of()));
        descriptors.put("B", new FeatureDescriptor("B", "x.B", "B", "1", Set.of("A"), Set.of()));

        List<String> logs = new ArrayList<>();
        FeatureLoadOrderResolver.Result result = FeatureLoadOrderResolver.resolveLoadOrder(
                descriptors.keySet(),
                descriptors::get,
                name -> descriptors.containsKey(name) ? name : null,
                logs::add
        );

        assertTrue(result.loadOrder().isEmpty());
        assertEquals(Set.of("A", "B"), result.skippedFeatures());
        assertTrue(logs.stream().anyMatch(msg -> msg.contains("Dependency cycle detected")));
    }
}
