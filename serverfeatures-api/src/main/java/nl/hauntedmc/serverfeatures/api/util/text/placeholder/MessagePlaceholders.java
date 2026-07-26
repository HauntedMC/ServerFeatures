package nl.hauntedmc.serverfeatures.api.util.text.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;

/**
 * Strongly-typed placeholder bag (public API unchanged).
 * Internally stores only strings; Components are serialized on add.
 */
public final class MessagePlaceholders {

    private final Map<String, String> values;

    private MessagePlaceholders(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    private Map<String, String> entries() {
        return values;
    }

    private boolean isEmpty() {
        return values.isEmpty();
    }

    // ---- Public API (unchanged) ----
    public static Builder builder() {
        return new Builder();
    }

    public static MessagePlaceholders empty() {
        return new MessagePlaceholders(Map.of());
    }

    /**
     * Replaces literal tokens like "{key}" with their string values.
     * Keys are applied longest-first to avoid "{a}" interfering with "{ab}".
     */
    public static String applyPlaceholders(String message, MessagePlaceholders placeholders) {
        if (message == null || placeholders == null || placeholders.isEmpty()) return message;

        var entries = new ArrayList<>(placeholders.entries().entrySet());

        entries.sort(Comparator.comparingInt((Map.Entry<String, ?> e) -> e.getKey().length()).reversed());

        String out = message;
        for (var e : entries) {
            out = out.replace(token(e.getKey()), e.getValue());
        }
        return out;
    }

    /**
     * Literal token format: "{key}"
     */
    private static String token(String key) {
        return "{" + key + "}";
    }

    // ---- Builder / factories ----
    public static final class Builder {
        private final Map<String, String> map = new LinkedHashMap<>();

        public Builder addString(String key, String value) {
            map.put(key, value == null ? "" : value);
            return this;
        }

        public Builder addNumber(String key, Number value) {
            map.put(key, value == null ? "0" : String.valueOf(value));
            return this;
        }

        public Builder addComponent(String key, Component value) {
            if (value == null) {
                map.put(key, "");
            } else {
                map.put(key, MiniMessage.miniMessage().serialize(value));
            }
            return this;
        }

        /**
         * Convenience: add by type.
         * Component -> MiniMessage, Number -> String.valueOf, else -> String.valueOf (null -> "").
         */
        public Builder add(String key, Object value) {
            if (value instanceof Component c) return addComponent(key, c);
            if (value instanceof Number n) return addNumber(key, n);
            return addString(key, value == null ? "" : String.valueOf(value));
        }

        public Builder addAll(MessagePlaceholders existing) {
            if (existing != null) this.map.putAll(existing.entries());
            return this;
        }

        public MessagePlaceholders build() {
            if (map.isEmpty()) return new MessagePlaceholders(Map.of());
            return new MessagePlaceholders(new LinkedHashMap<>(map));
        }
    }
}
