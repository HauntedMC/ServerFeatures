package nl.hauntedmc.serverfeatures.framework.lifecycle;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plugin-wide ownership registry shared by every feature lifecycle.
 */
final class FeatureCommandOwnership {

    private final Map<String, Object> ownersByLabel = new HashMap<>();

    synchronized String claim(Object owner, Collection<String> labels) {
        for (String label : labels) {
            String normalized = normalize(label);
            Object existing = ownersByLabel.get(normalized);
            if (existing != null && existing != owner) {
                return label;
            }
        }
        for (String label : labels) {
            ownersByLabel.put(normalize(label), owner);
        }
        return null;
    }

    synchronized void release(Object owner, Collection<String> labels) {
        for (String label : labels) {
            ownersByLabel.remove(normalize(label), owner);
        }
    }

    private static String normalize(String label) {
        return label.toLowerCase(Locale.ROOT);
    }
}
