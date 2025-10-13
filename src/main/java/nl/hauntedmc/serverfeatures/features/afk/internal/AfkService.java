package nl.hauntedmc.serverfeatures.features.afk.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.afk.AFK;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkEngine;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkService implements AfkServiceFacade {

    private final AFK feature;
    private final AfkEngine engine;

    private final Map<UUID, AfkPlayerState> states = new ConcurrentHashMap<>();

    public AfkService(AFK feature) {
        this.feature = feature;
        this.engine = new AfkEngine(this);
    }

    public void bootstrapOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) handleJoin(p);
    }

    public void handleJoin(Player p) {
        var s = states.computeIfAbsent(p.getUniqueId(), k -> new AfkPlayerState());
        s.touchActivity(System.currentTimeMillis());
    }

    public void handleLeave(Player p) {
        states.remove(p.getUniqueId());
    }

    public boolean isAfk(UUID uuid) {
        var s = states.get(uuid);
        return s != null && s.isAfk();
    }

    public void fire(AfkEvent event) {
        Player p = event.player();
        if (p == null) return;
        AfkPlayerState s = states.computeIfAbsent(p.getUniqueId(), k -> new AfkPlayerState());

        AfkDecision d = engine.evaluate(event, s);
        if (d == null || d.isNoop()) return;

        applyDecision(p, s, d, event.timestamp());
    }

    private void applyDecision(Player p, AfkPlayerState s, AfkDecision d, long now) {
        if (d.actions().contains(AfkDecisionType.CLEAR_SUSPICIOUS)) {
            s.setSuspicious(false);
            // optioneel maar aan te raden: voorkom direct her-flaggen op oude samples
            s.clearAntiTimes();
        }
        if (d.actions().contains(AfkDecisionType.FLAG_SUSPICIOUS)) s.setSuspicious(true);

        if (d.actions().contains(AfkDecisionType.LOCK_AFK)) {
            s.setAfkLockUntil(now + antiLockMs());
        }
        if (d.actions().contains(AfkDecisionType.UNLOCK_AFK)) {
            s.setAfkLockUntil(0L);
        }

        if (d.actions().contains(AfkDecisionType.LEAVE_AFK)) {
            if (s.isAfk()) {
                s.setAfk(false);
                s.setAfkSince(0L);
                s.touchActivity(now);
                sendSelf(p, "afk.disabled_self");
                if (broadcast()) broadcast("afk.broadcast_disabled", p.name());

                // --- BELANGRIJK: alles opschonen bij statuswissel ---
                s.clearAntiTimes();
                s.resetComboSignals();   // <<<<<<<<<<<<<<<<<<<<<<<<<<
                s.setAfkLockUntil(0L);
            }
        } else if (d.actions().contains(AfkDecisionType.ENTER_AFK)) {
            if (!s.isAfk()) {
                s.setAfk(true);
                s.setAfkSince(now);
                sendSelf(p, "afk.enabled_self");
                if (broadcast()) broadcast("afk.broadcast_enabled", p.name());

                s.clearAntiTimes();
                s.resetComboSignals();
            }
        }

        if (d.actions().contains(AfkDecisionType.TOUCH_ACTIVITY) && !s.isSuspicious()) {
            s.touchActivity(now);
        }
    }


    public void setAfk(Player p, boolean value) {
        AfkPlayerState s = states.computeIfAbsent(p.getUniqueId(), k -> new AfkPlayerState());
        long now = System.currentTimeMillis();
        if (value) {
            if (!s.isAfk()) {
                s.setAfk(true);
                s.setAfkSince(now);
                sendSelf(p, "afk.enabled_self");
                if (broadcast()) broadcast("afk.broadcast_enabled", p.name());
                s.clearAntiTimes();
                s.resetComboSignals();
            }
        } else {
            if (s.isAfk()) {
                s.setAfk(false);
                s.setAfkSince(0L);
                s.touchActivity(now);
                sendSelf(p, "afk.disabled_self");
                if (broadcast()) broadcast("afk.broadcast_disabled", p.name());
                s.clearAntiTimes();
                s.resetComboSignals();
                s.setAfkLockUntil(0L);
            }
        }
    }

    public void tickCheck() {
        long now = System.currentTimeMillis();
        int afkTimeout = getInt("afk_timeout_seconds", 600);
        if (afkTimeout <= 0) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            AfkPlayerState s = states.computeIfAbsent(p.getUniqueId(), k -> new AfkPlayerState());
            long last = s.lastActivity() == 0 ? now : s.lastActivity();

            if (!s.isAfk()) {
                if (now - last >= afkTimeout * 1000L) {
                    applyDecision(p, s,
                            AfkDecision.of(AfkPriority.MEDIUM, AfkDecisionType.ENTER_AFK),
                            now);
                }
            } else {
                if (getBool("kick_enabled", true)) {
                    int kickAfter = getInt("kick_timeout_seconds", 3600);
                    if (kickAfter > 0 && now - s.afkSince() >= kickAfter * 1000L) {
                        Component msg = feature.getLocalizationHandler().getMessage("afk.kicked").forAudience(p).build();
                        try { p.kick(msg); } catch (Throwable ignored) {}
                        states.remove(p.getUniqueId());
                    }
                }
            }
        }
    }

    private void sendSelf(Player p, String key) {
        try {
            p.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(p).build());
        } catch (Throwable ignored) {}
    }

    private boolean broadcast() {
        Object o = feature.getConfigHandler().getSetting("broadcast_on_state_change");
        return (o instanceof Boolean b) && b;
    }

    private void broadcast(String key, Component name) {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            try {
                pl.sendMessage(feature.getLocalizationHandler()
                        .getMessage(key)
                        .with("name", name)
                        .forAudience(pl).build());
            } catch (Throwable ignored) {}
        }
    }

    // --- AfkServiceFacade (config bridge) ---

    @Override public double moveThreshold() { return getDouble("movement_distance_threshold", 0.15D); }
    @Override public float rotateThreshold() { return (float) getDouble("rotation_threshold_degrees", 10.0D); }
    @Override public long comboWindowMs() { return getInt("combo_window_seconds", 30) * 1000L; }
    @Override public boolean antiEnabled() { return getBool("anti_afk.enabled", true); }
    @Override public long antiWindowMs() { return getInt("anti_afk.track_window_seconds", 120) * 1000L; }
    @Override public int antiMinSamples() { return getInt("anti_afk.min_samples", 6); }
    @Override public long antiMeanMinMs() { return getInt("anti_afk.mean_min_ms", 800); }
    @Override public long antiMeanMaxMs() { return getInt("anti_afk.mean_max_ms", 15000); }
    @Override public long antiStddevMaxMs() { return getInt("anti_afk.stddev_max_ms", 120); }
    @Override public long antiLockMs() { return getInt("anti_afk.lock_seconds", 60) * 1000L; }
    @Override public double verticalEpsilon() { return getDouble("movement_vertical_epsilon", 0.05D); }


    @Override
    public boolean isAfkCommand(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.startsWith("/")) s = s.substring(1);
        int sp = s.indexOf(' ');
        String first = (sp == -1 ? s : s.substring(0, sp)).toLowerCase(java.util.Locale.ROOT);
        return first.equals("afk") || first.endsWith(":afk");
    }

    private boolean getBool(String key, boolean def) {
        Object o = feature.getConfigHandler().getSetting(key);
        return (o instanceof Boolean b) ? b : def;
    }
    private int getInt(String key, int def) {
        Object o = feature.getConfigHandler().getSetting(key);
        return (o instanceof Number n) ? n.intValue() : def;
    }
    private double getDouble(String key, double def) {
        Object o = feature.getConfigHandler().getSetting(key);
        return (o instanceof Number n) ? n.doubleValue() : def;
    }

    public void cleanupOnDisable() {
        states.clear();
    }
}
