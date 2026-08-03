package nl.hauntedmc.serverfeatures.features.autopickup.model;

import java.util.Locale;

public enum DropScope {
    STRICT_DIRECT,
    EVENT_ALL;

    public static DropScope parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("drop-policy.scope cannot be null");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown drop-policy.scope '" + value + "'. Expected STRICT_DIRECT or EVENT_ALL.",
                    exception
            );
        }
    }
}
