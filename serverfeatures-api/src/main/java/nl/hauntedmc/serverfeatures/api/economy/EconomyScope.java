package nl.hauntedmc.serverfeatures.api.economy;

import java.util.Objects;

/** Stable resolved scope for one economy account. */
public record EconomyScope(EconomyScopeType type, String key) {
    public EconomyScope {
        Objects.requireNonNull(type, "type");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        key = key.trim();
    }
}
