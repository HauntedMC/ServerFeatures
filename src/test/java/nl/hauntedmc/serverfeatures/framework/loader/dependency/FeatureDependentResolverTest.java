package nl.hauntedmc.serverfeatures.framework.loader.dependency;

import nl.hauntedmc.serverfeatures.framework.loader.FeatureDescriptor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureDependentResolverTest {

    @Test
    void findsDependentsForResolvedTargetKey() {
        Map<String, FeatureDescriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("A", new FeatureDescriptor("A", "x.A", "A", "1", Set.of("core"), Set.of()));
        descriptors.put("B", new FeatureDescriptor("B", "x.B", "B", "1", Set.of("other"), Set.of()));
        descriptors.put("C", new FeatureDescriptor("C", "x.C", "C", "1", Set.of("Core"), Set.of()));

        List<String> dependents = FeatureDependentResolver.getDependentFeatures(
                "core",
                Set.of("A", "B", "C"),
                descriptors::get,
                dep -> dep.toLowerCase()
        ).stream().sorted().toList();

        assertEquals(List.of("A", "C"), dependents);
    }

    @Test
    void returnsEmptyWhenTargetMissing() {
        List<String> dependents = FeatureDependentResolver.getDependentFeatures(
                null,
                Set.of("A"),
                name -> null,
                name -> null
        );
        assertEquals(List.of(), dependents);
    }
}
