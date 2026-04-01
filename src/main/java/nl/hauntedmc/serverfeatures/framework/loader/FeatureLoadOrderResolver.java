package nl.hauntedmc.serverfeatures.framework.loader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

final class FeatureLoadOrderResolver {

    private enum LoadOrderState {
        VISITING,
        VISITED,
        FAILED
    }

    record Result(List<String> loadOrder, Set<String> skippedFeatures) {
    }

    private FeatureLoadOrderResolver() {
    }

    static Result resolveLoadOrder(
            Collection<String> featureNames,
            Function<String, FeatureDescriptor> descriptorProvider,
            Function<String, String> featureKeyResolver,
            Consumer<String> logger
    ) {
        List<String> loadOrder = new ArrayList<>();
        Map<String, LoadOrderState> states = new HashMap<>();
        Set<String> skippedFeatures = new LinkedHashSet<>();

        for (String featureName : featureNames) {
            if (!resolveFeatureLoadOrder(
                    featureName,
                    descriptorProvider,
                    featureKeyResolver,
                    logger,
                    states,
                    new ArrayDeque<>(),
                    loadOrder
            )) {
                skippedFeatures.add(featureName);
            }
        }

        return new Result(loadOrder, skippedFeatures);
    }

    private static boolean resolveFeatureLoadOrder(
            String featureName,
            Function<String, FeatureDescriptor> descriptorProvider,
            Function<String, String> featureKeyResolver,
            Consumer<String> logger,
            Map<String, LoadOrderState> states,
            Deque<String> path,
            List<String> loadOrder
    ) {
        LoadOrderState state = states.get(featureName);
        if (state == LoadOrderState.VISITED) {
            return true;
        }
        if (state == LoadOrderState.FAILED) {
            return false;
        }
        if (state == LoadOrderState.VISITING) {
            List<String> cyclePath = new ArrayList<>(path);
            cyclePath.add(featureName);
            logger.accept("Dependency cycle detected: " + String.join(" -> ", cyclePath));
            states.put(featureName, LoadOrderState.FAILED);
            return false;
        }

        FeatureDescriptor descriptor = descriptorProvider.apply(featureName);
        if (descriptor == null) {
            logger.accept("Feature '" + featureName + "' is not registered as available.");
            states.put(featureName, LoadOrderState.FAILED);
            return false;
        }

        states.put(featureName, LoadOrderState.VISITING);
        path.addLast(featureName);

        try {
            for (String dependency : descriptor.featureDependencies()) {
                String dependencyKey = featureKeyResolver.apply(dependency);
                if (dependencyKey == null) {
                    states.put(featureName, LoadOrderState.FAILED);
                    logger.accept(
                            "Feature '" + featureName + "' cannot be loaded because dependency '" + dependency + "' is unavailable."
                    );
                    return false;
                }

                if (!resolveFeatureLoadOrder(
                        dependencyKey,
                        descriptorProvider,
                        featureKeyResolver,
                        logger,
                        states,
                        path,
                        loadOrder
                )) {
                    states.put(featureName, LoadOrderState.FAILED);
                    logger.accept(
                            "Feature '" + featureName + "' cannot be loaded because dependency '" + dependencyKey + "' failed."
                    );
                    return false;
                }
            }

            states.put(featureName, LoadOrderState.VISITED);
            loadOrder.add(featureName);
            return true;
        } finally {
            path.removeLast();
        }
    }
}
