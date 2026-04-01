package nl.hauntedmc.serverfeatures.features.teleportation.internal;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Central cooldown store:
 * - Key = (player UUID, action)
 * - Value = last-use epoch millis
 */
public class TeleportState {

    private final Function<String, Object> configLookup;
    private final ConcurrentHashMap<CooldownKey, Long> lastUseMs = new ConcurrentHashMap<>();

    public TeleportState(Teleportation feature) {
        this(path -> feature.getConfigHandler().get(path));
    }

    public TeleportState(Function<String, Object> configLookup) {
        this.configLookup = java.util.Objects.requireNonNull(configLookup, "configLookup");
    }

    public int getCooldownSeconds(TeleportAction action) {
        String path = "cooldown_seconds." + action.configKey();
        Object v = configLookup.apply(path);
        if (v instanceof Number n) return n.intValue();
        return 10;
    }

    public long getLastUse(UUID playerId, TeleportAction action) {
        return lastUseMs.getOrDefault(new CooldownKey(playerId, action), 0L);
    }

    public void setLastUse(UUID playerId, TeleportAction action, long epochMillis) {
        if (epochMillis <= 0L) {
            reset(playerId, action);
        } else {
            lastUseMs.put(new CooldownKey(playerId, action), epochMillis);
        }
    }

    public void reset(UUID playerId, TeleportAction action) {
        lastUseMs.remove(new CooldownKey(playerId, action));
    }

    public void clearAll() {
        lastUseMs.clear();
    }

    public long remainingCooldownSeconds(UUID playerId, TeleportAction action, long nowMs) {
        int cdSec = getCooldownSeconds(action);
        if (cdSec <= 0) return 0;

        long last = getLastUse(playerId, action);
        if (last == 0L) return 0;

        long elapsedSec = (nowMs - last) / 1000L;
        long remaining = cdSec - elapsedSec;
        return Math.max(0, remaining);
    }

    public boolean tryStart(UUID playerId, TeleportAction action, long nowMs) {
        long remaining = remainingCooldownSeconds(playerId, action, nowMs);
        if (remaining > 0) return false;
        setLastUse(playerId, action, nowMs);
        return true;
    }

    private record CooldownKey(UUID playerId, TeleportAction action) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CooldownKey(UUID id, TeleportAction action1))) return false;
            return java.util.Objects.equals(playerId, id) && action == action1;
        }
    }
}
