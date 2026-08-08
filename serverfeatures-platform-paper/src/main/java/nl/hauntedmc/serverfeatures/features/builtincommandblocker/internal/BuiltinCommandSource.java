package nl.hauntedmc.serverfeatures.features.builtincommandblocker.internal;

import java.util.Locale;

public enum BuiltinCommandSource {
    MINECRAFT("minecraft"),
    BUKKIT("bukkit"),
    PAPER("paper"),
    SPIGOT("spigot"),
    SPARK("spark");

    private final String configKey;

    BuiltinCommandSource(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static BuiltinCommandSource fromNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }
        String normalized = namespace.toLowerCase(Locale.ROOT);
        for (BuiltinCommandSource source : values()) {
            if (source.configKey.equals(normalized)) {
                return source;
            }
        }
        return null;
    }
}
