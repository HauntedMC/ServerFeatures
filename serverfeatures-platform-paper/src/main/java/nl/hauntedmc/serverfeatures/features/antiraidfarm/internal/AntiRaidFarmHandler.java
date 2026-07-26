package nl.hauntedmc.serverfeatures.features.antiraidfarm.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class AntiRaidFarmHandler {

    private final int cooldownSeconds;
    private final boolean notify;

    // value = epoch millis when the raid was last allowed/triggered
    private final Cache<UUID, Long> lastRaidCache;

    public AntiRaidFarmHandler(int cooldownSeconds, boolean notify) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.notify = notify;

        this.lastRaidCache = CacheBuilder.newBuilder()
                .expireAfterWrite(this.cooldownSeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Permission bypass. Keep hard-coded to match current behavior.
     */
    public boolean isBypassed(Player p) {
        return p != null && p.hasPermission("serverfeatures.feature.antiraidfarm.bypass");
    }

    /**
     * Remaining seconds (rounded up) if on cooldown; otherwise empty.
     */
    public OptionalLong remainingSeconds(UUID playerId, long nowMillis) {
        Long last = lastRaidCache.getIfPresent(playerId);
        if (last == null) return OptionalLong.empty();
        long elapsedMs = Math.max(0L, nowMillis - last);
        long remainingMs = (cooldownSeconds * 1000L) - elapsedMs;
        if (remainingMs <= 0) return OptionalLong.empty();
        long secondsUp = (remainingMs + 999L) / 1000L;
        return OptionalLong.of(secondsUp);
    }

    /**
     * Marks a successful trigger (starts cooldown).
     */
    public void markTriggered(UUID playerId, long nowMillis) {
        lastRaidCache.put(playerId, nowMillis);
    }

    public boolean shouldNotify() {
        return notify;
    }

    /**
     * Snapshot of active cooldowns (those with remaining > 0), sorted by remaining desc.
     * Each entry: (name, uuid, remainingSeconds, totalSeconds).
     */
    public List<CooldownEntry> listActiveCooldowns() {
        final long now = System.currentTimeMillis();
        List<CooldownEntry> out = new ArrayList<>();

        for (Map.Entry<UUID, Long> e : lastRaidCache.asMap().entrySet()) {
            UUID uuid = e.getKey();
            Long startedAtMs = e.getValue();
            if (startedAtMs == null) continue;

            long elapsedMs = Math.max(0L, now - startedAtMs);
            long remainingMs = (cooldownSeconds * 1000L) - elapsedMs;
            if (remainingMs <= 0) continue;

            long remainingSec = (remainingMs + 999L) / 1000L;

            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            String name = off.getName() != null ? off.getName() : uuid.toString();

            out.add(new CooldownEntry(name, uuid, remainingSec, cooldownSeconds));
        }

        // sort by remaining desc
        out.sort(Comparator.comparingLong(CooldownEntry::remainingSeconds).reversed());
        return out;
    }

    /**
     * POJO for displaying in command.
     */
    public record CooldownEntry(String name, UUID uuid, long remainingSeconds, int totalSeconds) {
    }
}
