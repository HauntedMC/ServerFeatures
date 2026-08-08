package nl.hauntedmc.serverfeatures.api.io.config;

import java.util.*;

/**
 * Immutable view over a (normalized) config node.
 * Lets you traverse nested maps via dotted paths and fetch typed values without casts.
 */
public final class ConfigNode {
    private final Object value; // normalized via ConfigTypes.toPlain
    private final String path;  // dotted path for context

    private ConfigNode(Object normalizedValue, String path) {
        this.value = normalizedValue;
        this.path = path == null ? "" : path;
    }

    /**
     * Create a node from a raw (possibly ConfigurationSection) value.
     */
    public static ConfigNode ofRaw(Object raw, String path) {
        return new ConfigNode(ConfigTypes.toPlain(raw), path);
    }

    /**
     * @return true if this node is null/absent.
     */
    public boolean isNull() {
        return value == null;
    }

    /**
     * Return this node as a given type or default if missing/invalid.
     */
    public <T> T as(Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(value, type, defaultValue);
    }

    /**
     * Return this node as the given type (throws if invalid/missing).
     */
    public <T> T asRequired(Class<T> type) {
        T v = ConfigTypes.convert(value, type);
        if (v == null) throw new IllegalStateException("Required config missing at '" + path + "'");
        return v;
    }

    /**
     * If this is a map node, return child node by key.
     */
    public ConfigNode get(String key) {
        if (!(value instanceof Map<?, ?> m)) return new ConfigNode(null, childPath(key));
        Object raw = m.get(key);
        return new ConfigNode(ConfigTypes.toPlain(raw), childPath(key));
    }

    /**
     * Dotted-path traversal (e.g., "items.cosmetic-item.slot").
     */
    public ConfigNode getAt(String dottedPath) {
        if (dottedPath == null || dottedPath.isBlank()) return this;
        String[] parts = dottedPath.split("\\.");
        ConfigNode cur = this;
        for (String p : parts) cur = cur.get(p);
        return cur;
    }

    /**
     * If this is a map node, returns keys.
     */
    public Set<String> keys() {
        if (value instanceof Map<?, ?> m) {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (Object k : m.keySet()) out.add(String.valueOf(k));
            return out;
        }
        return Collections.emptySet();
    }

    /**
     * If this is a map node, returns children as nodes.
     */
    public Map<String, ConfigNode> children() {
        if (!(value instanceof Map<?, ?> m)) return Map.of();
        LinkedHashMap<String, ConfigNode> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            out.put(k, new ConfigNode(ConfigTypes.toPlain(e.getValue()), childPath(k)));
        }
        return out;
    }

    /**
     * Returns a list of T, converting elements as needed.
     */
    public <T> List<T> listOf(Class<T> elemType) {
        return ConfigTypes.convertList(value, elemType);
    }

    /**
     * Returns a {@code Map<String, V>} converting values to V.
     */
    public <V> Map<String, V> mapValues(Class<V> valueType) {
        return ConfigTypes.convertMapValues(value, valueType);
    }

    /**
     * Convenience: merge multiple string-list fields under this (map) node (accept single string or list).
     */
    public List<String> mergedStringList(String... keys) {
        if (!(value instanceof Map<?, ?> m) || keys == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String k : keys) {
            Object raw = m.get(k);
            if (raw == null) continue;
            List<String> part = ConfigTypes.convertList(raw, String.class);
            if (part != null) out.addAll(part);
        }
        return out;
    }

    /**
     * Underlying normalized value (Map/List/scalar).
     */
    public Object raw() {
        return value;
    }

    public String path() {
        return path;
    }

    private String childPath(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    @Override
    public String toString() {
        return "ConfigNode(" + path + ")";
    }
}
