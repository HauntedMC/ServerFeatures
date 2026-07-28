package nl.hauntedmc.serverfeatures.features.invtools.service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Prevents a playerdata session from opening after authentication has started but before Paper has
 * constructed the online player. The expiry covers denied or interrupted login attempts whose
 * configuration event never arrives.
 */
final class LoginFenceRegistry {

    private final Map<UUID, Long> deadlines = new ConcurrentHashMap<>();
    private final LongSupplier clockMillis;
    private final long timeoutMillis;

    LoginFenceRegistry(Duration timeout) {
        this(timeout, System::currentTimeMillis);
    }

    LoginFenceRegistry(Duration timeout, LongSupplier clockMillis) {
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        timeoutMillis = Objects.requireNonNull(timeout, "timeout").toMillis();
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    void mark(UUID playerId) {
        deadlines.put(Objects.requireNonNull(playerId, "playerId"), deadline());
    }

    boolean isFenced(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Long deadline = deadlines.get(playerId);
        if (deadline == null) {
            return false;
        }
        if (clockMillis.getAsLong() < deadline) {
            return true;
        }
        deadlines.remove(playerId, deadline);
        return false;
    }

    void clear(UUID playerId) {
        if (playerId != null) {
            deadlines.remove(playerId);
        }
    }

    void clear() {
        deadlines.clear();
    }

    private long deadline() {
        try {
            return Math.addExact(clockMillis.getAsLong(), timeoutMillis);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
