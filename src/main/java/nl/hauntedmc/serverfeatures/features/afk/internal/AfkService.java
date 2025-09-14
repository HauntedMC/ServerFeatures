package nl.hauntedmc.serverfeatures.features.afk.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AfkService {

    private final AFK feature;

    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();

    public AfkService(AFK feature) { this.feature = feature; }

    public boolean isAfk(UUID id) { return afk.contains(id); }

    public void handleJoin(Player p) { touch(p.getUniqueId()); }

    public void handleLeave(Player p) {
        UUID id = p.getUniqueId();
        afk.remove(id);
        lastActivity.remove(id);
        afkSince.remove(id);
    }

    public void onChat(Player p) {
        if (p == null) return;
        touch(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onCommand(Player p, String raw) {
        if (p == null) return;
        String first = firstToken(raw);
        if (first.equals("afk") || first.endsWith(":afk")) return;
        touch(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onInteract(Player p) {
        if (p == null) return;
        touch(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onMove(Player p,
                       double fx, double fy, double fz, float fyaw, float fpitch,
                       double tx, double ty, double tz, float tyaw, float tpitch) {
        if (p == null) return;
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        double dist2 = dx * dx + dy * dy + dz * dz;

        double moveThresh = getMoveThreshold();
        boolean moved = dist2 >= (moveThresh * moveThresh);

        float dyaw = Math.abs(tyaw - fyaw);
        float dpitch = Math.abs(tpitch - fpitch);
        float rotThresh = getRotateThreshold();
        boolean rotated = dyaw >= rotThresh || dpitch >= rotThresh;

        if (!moved && !rotated) return;

        touch(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void setAfk(Player p, boolean value) {
        if (p == null || !p.isOnline()) return;
        boolean cur = isAfk(p.getUniqueId());
        if (cur == value) return;

        if (value) {
            afk.add(p.getUniqueId());
            afkSince.put(p.getUniqueId(), System.currentTimeMillis());
            sendSelf(p, "afk.enabled_self");
            if (shouldBroadcast()) broadcast("afk.broadcast_enabled", Map.of("name", p.getName()));
        } else {
            afk.remove(p.getUniqueId());
            afkSince.remove(p.getUniqueId());
            touch(p.getUniqueId());
            sendSelf(p, "afk.disabled_self");
            if (shouldBroadcast()) broadcast("afk.broadcast_disabled", Map.of("name", p.getName()));
        }
    }

    public void tickCheck() {
        long now = System.currentTimeMillis();
        int afkTimeout = getAfkTimeoutSeconds();
        if (afkTimeout <= 0) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            long last = lastActivity.getOrDefault(id, now);

            if (!isAfk(id)) {
                if (now - last >= afkTimeout * 1000L) enterAfk(p);
            } else {
                if (isKickEnabled()) {
                    int kickAfter = getKickTimeoutSeconds();
                    if (kickAfter > 0) {
                        long since = afkSince.getOrDefault(id, now);
                        if (now - since >= kickAfter * 1000L) {
                            Component msg = feature.getLocalizationHandler().getMessage("afk.kicked").forAudience(p).build();
                            try { p.kick(msg); } catch (Throwable ignored) {}
                            afk.remove(id); afkSince.remove(id); lastActivity.remove(id);
                        }
                    }
                }
            }
        }
    }

    private void enterAfk(Player p) { setAfk(p, true); }
    private void leaveAfk(Player p) { setAfk(p, false); }
    private void touch(UUID id) { lastActivity.put(id, System.currentTimeMillis()); }

    private boolean shouldBroadcast() {
        Object o = feature.getConfigHandler().getSetting("broadcast_on_state_change");
        return (o instanceof Boolean b) ? b : false;
    }

    private int getAfkTimeoutSeconds() {
        Object o = feature.getConfigHandler().getSetting("afk_timeout_seconds");
        return (o instanceof Number n) ? n.intValue() : 600;
    }

    private boolean isKickEnabled() {
        Object o = feature.getConfigHandler().getSetting("kick_enabled");
        return (o instanceof Boolean b) && b;
    }

    private int getKickTimeoutSeconds() {
        Object o = feature.getConfigHandler().getSetting("kick_timeout_seconds");
        return (o instanceof Number n) ? n.intValue() : 3600;
    }

    private double getMoveThreshold() {
        Object o = feature.getConfigHandler().getSetting("movement_distance_threshold");
        return (o instanceof Number n) ? n.doubleValue() : 0.15D;
    }

    private float getRotateThreshold() {
        Object o = feature.getConfigHandler().getSetting("rotation_threshold_degrees");
        return (o instanceof Number n) ? n.floatValue() : 10.0F;
    }

    private void sendSelf(Player p, String key) {
        try {
            p.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(p).build());
        } catch (Throwable ignored) {}
    }

    private void broadcast(String key, Map<String, String> ph) {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            try {
                pl.sendMessage(feature.getLocalizationHandler().getMessage(key).withPlaceholders(ph).forAudience(pl).build());
            } catch (Throwable ignored) {}
        }
    }

    private static String firstToken(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("/")) s = s.substring(1);
        int sp = s.indexOf(' ');
        return (sp == -1 ? s : s.substring(0, sp)).toLowerCase(Locale.ROOT);
    }

    public void cleanupOnDisable() {
        afk.clear();
        lastActivity.clear();
        afkSince.clear();
    }
}
