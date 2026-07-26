package nl.hauntedmc.serverfeatures.framework.loader;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class FeatureKeyResolver {

    private FeatureKeyResolver() {
    }

    static String resolveFeatureKey(
            String inputName,
            Map<String, FeatureDescriptor> availableFeatures,
            Set<String> loadedFeatureNames,
            Function<String, String> loadedFeatureDisplayNameProvider
    ) {
        if (inputName == null) {
            return null;
        }

        String candidate = inputName.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        if (availableFeatures.containsKey(candidate) || loadedFeatureNames.contains(candidate)) {
            return candidate;
        }

        String availableCaseMatch = findCaseInsensitiveMatch(candidate, availableFeatures.keySet());
        if (availableCaseMatch != null) {
            return availableCaseMatch;
        }
        String loadedCaseMatch = findCaseInsensitiveMatch(candidate, loadedFeatureNames);
        if (loadedCaseMatch != null) {
            return loadedCaseMatch;
        }

        for (FeatureDescriptor descriptor : availableFeatures.values()) {
            if (candidate.equalsIgnoreCase(descriptor.featureName())) {
                return descriptor.registryName();
            }

            String simpleClassName = simpleClassName(descriptor.featureClassName());
            if (candidate.equalsIgnoreCase(simpleClassName)) {
                return descriptor.registryName();
            }
        }

        for (String loadedKey : loadedFeatureNames) {
            String loadedName = loadedFeatureDisplayNameProvider.apply(loadedKey);
            if (loadedName != null && candidate.equalsIgnoreCase(loadedName)) {
                return loadedKey;
            }
        }

        return null;
    }

    static String findCaseInsensitiveMatch(String candidate, Collection<String> values) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return value;
            }
        }
        return null;
    }

    static String simpleClassName(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
    }

    static boolean isValidFeatureKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }
}
