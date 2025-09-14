package nl.hauntedmc.serverfeatures.features.afk.internal;

import nl.hauntedmc.serverfeatures.features.afk.AFK;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core AFK logic: state, detection, kick, and name decoration.
 * Smart detection:
 * - Activity: meaningful movement (distance > threshold) or rotation (> threshold), interactions, chat, commands.
 * - AFK set when no activity for afk_timeout_seconds.
 * - AFK cleared by: chat, commands, or a walk+interact combo within a configured window.
 */
public class AfkService {

    public static final String PERM_KICK_BYPASS = "serverfeatures.feature.afk.kick.bypass";

    private final AFK feature;

    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();

    // For "walk+interact" combo
    private final Map<UUID, Long> lastWalk = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();

    public AfkService(AFK feature) {
        this.feature = feature;
    }

    public boolean isAfk(UUID id) {
        return afk.contains(id);
    }

    public void handleJoin(Player p) {
        touchActivity(p.getUniqueId());
    }

    public void handleLeave(Player p) {
        UUID id = p.getUniqueId();
        afk.remove(id);
        lastActivity.remove(id);
        afkSince.remove(id);
        lastWalk.remove(id);
        lastInteract.remove(id);
    }

    /* ---------------- Public toggling ---------------- */

    public void setAfk(Player p, boolean value) {
        if (p == null || !p.isOnline()) return;

        boolean current = isAfk(p.getUniqueId());
        if (current == value) return;

        if (value) {
            afk.add(p.getUniqueId());
            afkSince.put(p.getUniqueId(), System.currentTimeMillis());
            if (shouldBroadcast()) {
                Bukkit.getOnlinePlayers().forEach(pl -> {
                    try {
                        pl.sendMessage(feature.getLocalizationHandler()
                                .getMessage("afk.broadcast_enabled")
                                .withPlaceholders(Map.of("name", p.getName()))
                                .forAudience(pl)
                                .build());
                    } catch (Throwable ignored) {
                    }
                });
            }
        } else {
            afk.remove(p.getUniqueId());
            afkSince.remove(p.getUniqueId());
            touchActivity(p.getUniqueId());
            if (shouldBroadcast()) {
                Bukkit.getOnlinePlayers().forEach(pl -> {
                    try {
                        pl.sendMessage(feature.getLocalizationHandler()
                                .getMessage("afk.broadcast_disabled")
                                .withPlaceholders(Map.of("name", p.getName()))
                                .forAudience(pl)
                                .build());
                    } catch (Throwable ignored) {
                    }
                });
            }
        }
    }

    /* ---------------- Detection inputs ---------------- */

    public void onChat(Player p) {
        if (p == null) return;
        touchActivity(p.getUniqueId());
        if (isAfk(p.getUniqueId())) setAfk(p, false);
    }

    public void onCommand(Player p) {
        if (p == null) return;
        touchActivity(p.getUniqueId());
        if (isAfk(p.getUniqueId())) setAfk(p, false);
    }

    public void onInteract(Player p) {
        if (p == null) return;
        long now = System.currentTimeMillis();
        lastInteract.put(p.getUniqueId(), now);
        // If AFK, check combo (interact + recent walk)
        if (isAfk(p.getUniqueId())) {
            if (walkInteractComboSatisfied(p.getUniqueId(), now)) {
                setAfk(p, false);
            }
        } else {
            touchActivity(p.getUniqueId());
        }
    }

    public void onMove(Player p, double fromX, double fromY, double fromZ, float fromYaw, float fromPitch,
                       double toX, double toY, double toZ, float toYaw, float toPitch) {
        if (p == null) return;
        double distSq = (toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY) + (toZ - fromZ) * (toZ - fromZ);
        double distThreshold = getMoveThreshold(); // in blocks
        boolean movedEnough = distSq >= (distThreshold * distThreshold);

        float yawDiff = Math.abs(toYaw - fromYaw);
        float pitchDiff = Math.abs(toPitch - fromPitch);
        float rotateThreshold = getRotateThreshold();
        boolean rotatedEnough = yawDiff >= rotateThreshold || pitchDiff >= rotateThreshold;

        if (!movedEnough && !rotatedEnough) return;

        if (movedEnough) {
            long now = System.currentTimeMillis();
            lastWalk.put(p.getUniqueId(), now);
            if (isAfk(p.getUniqueId())) {
                // Only clear AFK if interact also happened recently
                if (walkInteractComboSatisfied(p.getUniqueId(), now)) {
                    setAfk(p, false);
                }
            } else {
                touchActivity(p.getUniqueId());
            }
        } else {
            // rotation only, counts as activity but doesn't satisfy the walk+interact combo
            if (!isAfk(p.getUniqueId())) touchActivity(p.getUniqueId());
        }
    }

    private boolean walkInteractComboSatisfied(UUID id, long now) {
        long windowMs = getWalkInteractWindowMs();
        Long w = lastWalk.get(id);
        Long i = lastInteract.get(id);
        return (w != null && Math.abs(now - w) <= windowMs) &&
                (i != null && Math.abs(now - i) <= windowMs);
    }

    /* ---------------- Periodic check ---------------- */

    public void tickCheck() {
        long now = System.currentTimeMillis();
        int afkTimeout = getAfkTimeoutSeconds();
        if (afkTimeout <= 0) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            long last = lastActivity.getOrDefault(id, now);
            if (!isAfk(id)) {
                if ((now - last) >= afkTimeout * 1000L) {
                    setAfk(p, true);
                }
            } else {
                // Consider kick
                if (isKickEnabled() && !p.hasPermission(PERM_KICK_BYPASS)) {
                    long since = afkSince.getOrDefault(id, now);
                    int kickAfter = getKickTimeoutSeconds();
                    if (kickAfter > 0 && (now - since) >= kickAfter * 1000L) {
                        try {
                            p.kick(feature.getLocalizationHandler()
                                    .getMessage("afk.kicked")
                                    .forAudience(p)
                                    .build());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
    }

    /* ---------------- Utils & Config access ---------------- */
    private void touchActivity(UUID id) {
        lastActivity.put(id, System.currentTimeMillis());
    }

    private boolean shouldBroadcast() {
        Object o = feature.getConfigHandler().getSetting("broadcast_on_state_change");
        return !(o instanceof Boolean) || (Boolean) o;
    }

    private int getAfkTimeoutSeconds() {
        Object o = feature.getConfigHandler().getSetting("afk_timeout_seconds");
        return (o instanceof Number) ? ((Number) o).intValue() : 300;
    }

    private boolean isKickEnabled() {
        Object o = feature.getConfigHandler().getSetting("kick_enabled");
        return (o instanceof Boolean) && (Boolean) o;
    }

    private int getKickTimeoutSeconds() {
        Object o = feature.getConfigHandler().getSetting("kick_timeout_seconds");
        return (o instanceof Number) ? ((Number) o).intValue() : 900;
    }

    private long getWalkInteractWindowMs() {
        Object o = feature.getConfigHandler().getSetting("walk_interact_window_seconds");
        int s = (o instanceof Number) ? ((Number) o).intValue() : 3;
        return s * 1000L;
    }

    private double getMoveThreshold() {
        Object o = feature.getConfigHandler().getSetting("movement_distance_threshold");
        return (o instanceof Number) ? ((Number) o).doubleValue() : 0.15D;
    }

    private float getRotateThreshold() {
        Object o = feature.getConfigHandler().getSetting("rotation_threshold_degrees");
        return (o instanceof Number) ? ((Number) o).floatValue() : 15.0F;
    }

    public void cleanupOnDisable() {
        afk.clear();
        lastActivity.clear();
        afkSince.clear();
        lastWalk.clear();
        lastInteract.clear();
    }
}