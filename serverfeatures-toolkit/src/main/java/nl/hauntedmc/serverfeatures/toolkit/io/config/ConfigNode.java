package nl.hauntedmc.serverfeatures.toolkit.io.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable view over a normalized configuration node. */
public final class ConfigNode {
    private final Object value;
    private final String path;

    private ConfigNode(Object normalizedValue, String path) {
        this.value = normalizedValue;
        this.path = path == null ? "" : path;
    }

    public static ConfigNode ofRaw(Object raw, String path) {
        return new ConfigNode(ConfigTypes.toPlain(raw), path);
    }

    public boolean isNull() { return value == null; }
    public boolean isPresent() { return !isNull(); }
    public <T> T as(Class<T> type, T defaultValue) { return ConfigTypes.convertOrDefault(value, type, defaultValue); }

    public <T> T asRequired(Class<T> type) {
        T converted = ConfigTypes.convert(value, type);
        if (converted == null) throw new IllegalStateException("Required config missing at '" + path + "'");
        return converted;
    }

    public ConfigNode get(String key) {
        if (!(value instanceof Map<?, ?> map)) return new ConfigNode(null, childPath(key));
        return new ConfigNode(ConfigTypes.toPlain(map.get(key)), childPath(key));
    }

    public ConfigNode getAt(String dottedPath) {
        if (dottedPath == null || dottedPath.isBlank()) return this;
        ConfigNode current = this;
        for (String part : dottedPath.split("\\.")) current = current.get(part);
        return current;
    }

    public Set<String> keys() {
        if (!(value instanceof Map<?, ?> map)) return Collections.emptySet();
        LinkedHashSet<String> output = new LinkedHashSet<>();
        for (Object key : map.keySet()) output.add(String.valueOf(key));
        return output;
    }

    public Map<String, ConfigNode> children() {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, ConfigNode> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            output.put(key, new ConfigNode(ConfigTypes.toPlain(entry.getValue()), childPath(key)));
        }
        return output;
    }

    public <T> List<T> listOf(Class<T> elementType) { return ConfigTypes.convertList(value, elementType); }
    public <V> Map<String, V> mapValues(Class<V> valueType) { return ConfigTypes.convertMapValues(value, valueType); }
    public Object raw() { return value; }
    public String path() { return path; }
    private String childPath(String key) { return path.isEmpty() ? key : path + "." + key; }

    @Override
    public String toString() { return "ConfigNode(" + path + ")"; }
}
