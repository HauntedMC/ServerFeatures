package nl.hauntedmc.serverfeatures.api.io.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A type-safe wrapper around a configurable key-value map.
 * Supports defaulting, type safety, and future extensions like change tracking or validation.
 */
public class ConfigMap {
    private final Map<String, Object> values = new HashMap<>();

    public ConfigMap put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public Object get(String key) {
        return values.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = values.get(key);
        if (value == null) return null;
        if (!type.isInstance(value)) {
            throw new ClassCastException("Config key '" + key + "' is not of type " + type.getSimpleName());
        }
        return (T) value;
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return values.entrySet();
    }


    public Map<String, Object> toMap() {
        return new HashMap<>(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
