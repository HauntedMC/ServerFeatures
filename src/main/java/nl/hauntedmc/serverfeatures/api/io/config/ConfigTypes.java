package nl.hauntedmc.serverfeatures.api.io.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared conversion + normalization utilities for config values.
 * - Deeply normalizes Bukkit ConfigurationSection into {@code LinkedHashMap<String, Object>}
 * - Converts scalars to target types (String/boolean/int/long/double/enum)
 * - Converts Lists (with nested normalization) and Maps with typed values
 */
public final class ConfigTypes {
    private ConfigTypes() {
    }

    /**
     * Deeply normalizes ConfigurationSection -> Map and nested Lists/Maps.
     */
    public static Object toPlain(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case ConfigurationSection section -> {
                Map<String, Object> raw = section.getValues(false);
                LinkedHashMap<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    out.put(e.getKey(), toPlain(e.getValue()));
                }
                return out;
            }
            case Map<?, ?> map -> {
                LinkedHashMap<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(String.valueOf(e.getKey()), toPlain(e.getValue()));
                }
                return out;
            }
            case List<?> list -> {
                ArrayList<Object> out = new ArrayList<>(list.size());
                for (Object el : list) out.add(toPlain(el));
                return out;
            }
            default -> {
            }
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;

        if (type == Map.class) {
            Object plain = toPlain(value);
            if (plain instanceof Map<?, ?>) return (T) plain;
            throw typeError("Map", value);
        }
        if (type == List.class) {
            Object plain = toPlain(value);
            if (plain instanceof List<?>) return (T) plain;
            throw typeError("List", value);
        }
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            return switch (value) {
                case Boolean b -> (T) b;
                case Number n -> (T) Boolean.valueOf(n.intValue() != 0);
                case String s -> (T) Boolean.valueOf(Boolean.parseBoolean(s));
                default -> throw typeError("boolean", value);
            };
        }
        if (type == Integer.class || type == int.class) {
            if (value instanceof Number n) return (T) Integer.valueOf(n.intValue());
            if (value instanceof String s) return (T) Integer.valueOf(Integer.parseInt(s.trim()));
            throw typeError("int", value);
        }
        if (type == Long.class || type == long.class) {
            if (value instanceof Number n) return (T) Long.valueOf(n.longValue());
            if (value instanceof String s) return (T) Long.valueOf(Long.parseLong(s.trim()));
            throw typeError("long", value);
        }
        if (type == Double.class || type == double.class) {
            if (value instanceof Number n) return (T) Double.valueOf(n.doubleValue());
            if (value instanceof String s) return (T) Double.valueOf(Double.parseDouble(s.trim()));
            throw typeError("double", value);
        }
        if (type == Float.class || type == float.class) {
            if (value instanceof Number n) return (T) Float.valueOf(n.floatValue());
            if (value instanceof String s) return (T) Float.valueOf(Float.parseFloat(s.trim()));
            throw typeError("float", value);
        }
        if (type.isEnum()) {
            if (value instanceof String s) {
                @SuppressWarnings("rawtypes")
                Class<? extends Enum> ec = (Class<? extends Enum>) type;
                for (Object c : ec.getEnumConstants()) {
                    Enum<?> e = (Enum<?>) c;
                    if (e.name().equalsIgnoreCase(s.trim())) return (T) e;
                }
                throw new IllegalArgumentException("Unknown enum constant '" + s + "' for " + type.getName());
            }
            throw typeError("enum " + type.getName(), value);
        }

        throw new IllegalArgumentException("Unsupported conversion to " + type.getName() +
                " from " + value.getClass().getName());
    }

    public static <T> T convertOrDefault(Object value, Class<T> type, T defaultValue) {
        try {
            T v = convert(value, type);
            return v != null ? v : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static <T> List<T> convertList(Object raw, Class<T> elemType) {
        if (raw == null) return null;
        Object n = toPlain(raw);
        if (!(n instanceof List<?> src)) {
            T single = tryConvert(n, elemType);
            if (single != null) return List.of(single);
            throw typeError("List<" + elemType.getSimpleName() + ">", n);
        }
        List<T> out = new ArrayList<>(src.size());
        for (int i = 0; i < src.size(); i++) {
            Object el = src.get(i);
            try {
                out.add(convert(el, elemType));
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Invalid element at index " + i + " in list — expected " + elemType.getSimpleName() +
                                " but got " + (el == null ? "null" : el.getClass().getName()));
            }
        }
        return out;
    }

    public static <V> Map<String, V> convertMapValues(Object raw, Class<V> valueType) {
        if (raw == null) return null;
        Object n = toPlain(raw);
        if (!(n instanceof Map<?, ?> m)) {
            throw typeError("Map<String," + valueType.getSimpleName() + ">", n);
        }
        LinkedHashMap<String, V> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            out.put(String.valueOf(e.getKey()), convert(e.getValue(), valueType));
        }
        return out;
    }

    private static <T> T tryConvert(Object v, Class<T> type) {
        try {
            return convert(v, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static IllegalArgumentException typeError(String expected, Object got) {
        return new IllegalArgumentException("Expected " + expected + " but got " +
                (got == null ? "null" : got.getClass().getName()));
    }
}
