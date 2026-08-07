package nl.hauntedmc.serverfeatures.api.service;

import java.util.Optional;
import java.util.OptionalLong;

/** Reload-safe reference to an optional feature capability. */
public interface CapabilityRef<T> {
    Class<T> type();
    Optional<T> get();

    default OptionalLong generation() {
        return isAvailable() ? OptionalLong.of(1L) : OptionalLong.empty();
    }

    default boolean isAvailable() {
        return get().isPresent();
    }

    default T require() {
        return get().orElseThrow(() -> new CapabilityUnavailableException(type()));
    }
}
