package nl.hauntedmc.serverfeatures.api.util.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly-typed placeholder bag.
 * Supports String, Number, boolean (as text), and Component values.
 * String/Number/boolean are applied to the raw string before MiniMessage parsing
 * (with MiniMessage tag-escaping). Component values are applied *after* parsing.
 */
public final class MessagePlaceholders {

    public static String applyPlaceholders(String message, MessagePlaceholders placeholders) {
        if (message == null || placeholders == null || placeholders.isEmpty()) return message;
        String out = message;

        for (Map.Entry<String, MessagePlaceholders.Value> e : placeholders.entries().entrySet()) {
            String token = token(e.getKey());
            Value value = e.getValue();
            if (!e.getValue().isComponent()) {
                out = out.replace(token, value.asEscapedString());
            } else {
                String mm = MiniMessage.miniMessage().serialize(value.asComponentOrNull());
                out = out.replace(token, mm);
            }
        }
        return out;
    }

    /**
     * Literal token format: "{key}"
     */
    private static String token(String key) {
        return "{" + key + "}";
    }

    /**
     * Escapes a value for safe MiniMessage injection.
     */
    private static String escapeMM(String s) {
        return MiniMessage.miniMessage().escapeTags(s);
    }

    // ---- Value kinds ----
    private sealed interface Value permits StrVal, NumVal, CompVal {
        default boolean isComponent() {
            return this instanceof CompVal;
        }

        default Component asComponentOrNull() {
            return (this instanceof CompVal cv) ? cv.value() : null;
        }

        /**
         * String form used for *pre-parse* replacement (non-components only).
         */
        default String asEscapedString() {
            return switch (this) {
                case StrVal sv -> escapeMM(sv.value());
                case NumVal nv -> escapeMM(String.valueOf(nv.value()));
                default -> throw new IllegalStateException("Component values are not replaced pre-parse");
            };
        }
    }

    private static final class StrVal implements Value {
        private final String value;

        public StrVal(String value) {
            this.value = value == null ? "" : value;
        }

        public String value() {
            return value;
        }
    }

    private static final class NumVal implements Value {
        private final Number value;

        public NumVal(Number value) {
            this.value = value == null ? 0 : value;
        }

        public Number value() {
            return value;
        }
    }

    private static final class CompVal implements Value {
        private final Component value;

        public CompVal(Component value) {
            this.value = Objects.requireNonNull(value, "component");
        }

        public Component value() {
            return value;
        }
    }

    // ---- Data ----
    private final Map<String, Value> values;

    private MessagePlaceholders(Map<String, Value> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    private Map<String, Value> entries() {
        return values;
    }

    private boolean isEmpty() {
        return values.isEmpty();
    }

    // ---- Builders / factories ----
    public static Builder builder() {
        return new Builder();
    }

    public static MessagePlaceholders empty() {
        return new MessagePlaceholders(Map.of());
    }


    public static final class Builder {
        private final Map<String, Value> map = new LinkedHashMap<>();

        public Builder addString(String key, String value) {
            map.put(key, new StrVal(value));
            return this;
        }

        public Builder addNumber(String key, Number value) {
            map.put(key, new NumVal(value));
            return this;
        }

        public Builder addComponent(String key, Component value) {
            map.put(key, new CompVal(value));
            return this;
        }

        /**
         * Convenience: add by type.
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
            return new MessagePlaceholders(new LinkedHashMap<>(map));
        }
    }
}
