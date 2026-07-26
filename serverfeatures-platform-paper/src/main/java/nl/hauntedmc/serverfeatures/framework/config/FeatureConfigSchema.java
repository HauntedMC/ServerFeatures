package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class FeatureConfigSchema {

    enum Kind {
        MAP,
        LIST,
        BOOLEAN,
        NUMBER,
        STRING,
        OTHER
    }

    private FeatureConfigSchema() {
    }

    static Set<String> topLevelKeysFromDefaults(ConfigMap defaults) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : defaults.keySet()) {
            int dot = key.indexOf('.');
            out.add(dot >= 0 ? key.substring(0, dot) : key);
        }
        return out;
    }

    static Kind classify(Object normalized) {
        if (normalized == null) {
            return null;
        }
        if (normalized instanceof Map) {
            return Kind.MAP;
        }
        if (normalized instanceof java.util.List) {
            return Kind.LIST;
        }
        if (normalized instanceof Boolean) {
            return Kind.BOOLEAN;
        }
        if (normalized instanceof Number) {
            return Kind.NUMBER;
        }
        if (normalized instanceof CharSequence) {
            return Kind.STRING;
        }
        return Kind.OTHER;
    }

    static Kind expectedKindForTopKey(String topKey, ConfigMap defaults) {
        if (defaults.contains(topKey)) {
            return classify(defaults.get(topKey));
        }
        String prefix = topKey + ".";
        for (String k : defaults.keySet()) {
            if (k.startsWith(prefix)) {
                return Kind.MAP;
            }
        }
        return null;
    }
}
