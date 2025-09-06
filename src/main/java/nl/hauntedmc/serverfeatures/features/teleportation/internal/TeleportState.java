package nl.hauntedmc.serverfeatures.features.teleportation.internal;

import nl.hauntedmc.serverfeatures.features.teleportation.Teleportation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central cooldown store:
 * - Key = (player UUID, action)
 * - Value = last-use epoch millis
 */
public class TeleportState {

    private final Teleportation feature;
    private final ConcurrentHashMap<CooldownKey, Long> lastUseMs = new ConcurrentHashMap<>();

    public TeleportState(Teleportation feature) {
        this.feature = feature;
    }

    public int getCooldownSeconds(TeleportAction action) {
        String path = "cooldown_seconds." + action.configKey();
        Object v = feature.getConfigHandler().getSetting(path);
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

    private static final class CooldownKey {
        private final UUID playerId;
        private final TeleportAction action;
        private CooldownKey(UUID playerId, TeleportAction action) {
            this.playerId = playerId;
            this.action = action;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CooldownKey that)) return false;
            return java.util.Objects.equals(playerId, that.playerId) && action == that.action;
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(playerId, action);
        }
    }
}
