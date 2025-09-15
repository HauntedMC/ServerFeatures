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

    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWeak = new ConcurrentHashMap<>();
    private final Map<UUID, AntiPattern> anti = new ConcurrentHashMap<>();

    public AfkService(AFK feature) { this.feature = feature; }

    public boolean isAfk(UUID id) { return afk.contains(id); }

    public void bootstrapOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) handleJoin(p);
    }

    public void handleJoin(Player p) { touch(p.getUniqueId()); }

    public void handleLeave(Player p) {
        UUID id = p.getUniqueId();
        afk.remove(id);
        lastActivity.remove(id);
        afkSince.remove(id);
        lastMove.remove(id);
        lastWeak.remove(id);
        anti.remove(id);
    }

    public void onChat(Player p) {
        if (p == null) return;
        touch(p.getUniqueId());
        clearSuspicion(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onCommand(Player p, String raw) {
        if (p == null) return;
        String first = firstToken(raw);
        if (first.equals("afk") || first.endsWith(":afk")) return;
        touch(p.getUniqueId());
        clearSuspicion(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onInventoryClick(Player p) {
        if (p == null) return;
        touch(p.getUniqueId());
        clearSuspicion(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onStrongAction(Player p) {
        if (p == null) return;
        touch(p.getUniqueId());
        clearSuspicion(p.getUniqueId());
        if (isAfk(p.getUniqueId())) leaveAfk(p);
    }

    public void onWeakAction(Player p) {
        if (p == null) return;
        long now = System.currentTimeMillis();
        lastWeak.put(p.getUniqueId(), now);
        recordAnti(p.getUniqueId(), now);

        if (!isAfk(p.getUniqueId())) {
            return;
        }

        if (isAntiEnabled() && isSuspicious(p.getUniqueId())) {
            return;
        }

        Long lm = lastMove.get(p.getUniqueId());
        if (lm != null && withinComboWindow(now, lm)) leaveAfk(p);
    }

    public void onMove(Player p,
                       double fx, double fy, double fz, float fyaw, float fpitch,
                       double tx, double ty, double tz, float tyaw, float tpitch) {
        if (p == null) return;
        double dx = tx - fx, dz = tz - fz, dy = ty - fy;

        double horizontal2 = dx * dx + dz * dz;
        double moveThresh = getMoveThreshold();
        boolean movedHoriz = horizontal2 >= (moveThresh * moveThresh);

        float dyaw = Math.abs(tyaw - fyaw);
        float dpitch = Math.abs(tpitch - fpitch);
        float rotThresh = getRotateThreshold();
        boolean rotated = dyaw >= rotThresh || dpitch >= rotThresh;

        boolean verticalOnly = !movedHoriz && Math.abs(dy) > 0.0;

        if (!movedHoriz && !rotated && !verticalOnly) return;

        long now = System.currentTimeMillis();
        recordAnti(p.getUniqueId(), now);

        if (movedHoriz || rotated) {
            lastMove.put(p.getUniqueId(), now);
        }

        if (!isAfk(p.getUniqueId())) {
            if (isAntiEnabled() && isSuspicious(p.getUniqueId())) return;
            if (movedHoriz || rotated) touch(p.getUniqueId());
            return;
        }

        if (isAntiEnabled() && isSuspicious(p.getUniqueId())) {
            return;
        }

        Long lw = lastWeak.get(p.getUniqueId());
        if (lw != null && withinComboWindow(now, lw) && (movedHoriz || rotated)) leaveAfk(p);
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
            lastMove.remove(p.getUniqueId());
            lastWeak.remove(p.getUniqueId());
            clearSuspicion(p.getUniqueId());
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
                            lastMove.remove(id); lastWeak.remove(id); anti.remove(id);
                        }
                    }
                }
            }
        }
    }

    private void enterAfk(Player p) { setAfk(p, true); }
    private void leaveAfk(Player p) { setAfk(p, false); }

    private void touch(UUID id) { lastActivity.put(id, System.currentTimeMillis()); }

    private boolean withinComboWindow(long now, long otherTs) {
        long windowMs = getComboWindowMillis();
        long dt = Math.abs(now - otherTs);
        return dt >= 0 && dt <= windowMs;
    }

    private void recordAnti(UUID id, long now) {
        if (!isAntiEnabled()) return;
        AntiPattern ap = anti.computeIfAbsent(id, k -> new AntiPattern());
        ap.record(now, getAntiTrackWindowMillis());
    }

    private boolean isSuspicious(UUID id) {
        AntiPattern ap = anti.get(id);
        return ap != null && ap.isSuspicious(
                getAntiMinSamples(),
                getAntiMeanMinMs(),
                getAntiMeanMaxMs(),
                getAntiStddevMaxMs()
        );
    }

    private void clearSuspicion(UUID id) {
        AntiPattern ap = anti.get(id);
        if (ap != null) ap.reset();
    }

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

    private long getComboWindowMillis() {
        Object o = feature.getConfigHandler().getSetting("combo_window_seconds");
        int s = (o instanceof Number n) ? n.intValue() : 30;
        return Math.max(0, s) * 1000L;
    }

    private boolean isAntiEnabled() {
        Object o = feature.getConfigHandler().getSetting("anti_afk.enabled");
        return !(o instanceof Boolean b) || b;
    }

    private long getAntiTrackWindowMillis() {
        Object o = feature.getConfigHandler().getSetting("anti_afk.track_window_seconds");
        int s = (o instanceof Number n) ? n.intValue() : 120;
        return Math.max(0, s) * 1000L;
    }

    private int getAntiMinSamples() {
        Object o = feature.getConfigHandler().getSetting("anti_afk.min_samples");
        return (o instanceof Number n) ? n.intValue() : 6;
    }

    private long getAntiMeanMinMs() {
        Object o = feature.getConfigHandler().getSetting("anti_afk.mean_min_ms");
        return (o instanceof Number n) ? n.longValue() : 800L;
    }

    private long getAntiMeanMaxMs() {
        Object o = feature.getConfigHandler().getSetting("anti_afk.mean_max_ms");
        return (o instanceof Number n) ? n.longValue() : 15000L;
    }

    private long getAntiStddevMaxMs() {
        Object o = feature.getConfigHandler().getSetting("anti_afk.stddev_max_ms");
        return (o instanceof Number n) ? n.longValue() : 120L;
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
        lastMove.clear();
        lastWeak.clear();
        anti.clear();
    }

    private static final class AntiPattern {
        private final Deque<Long> times = new ArrayDeque<>();

        void record(long now, long windowMs) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
                times.removeFirst();
            }
        }

        boolean isSuspicious(int minSamples, long meanMin, long meanMax, long stddevMax) {
            if (times.size() < minSamples + 1) return false;
            long[] intervals = toIntervals(times);
            if (intervals.length < minSamples) return false;

            double mean = mean(intervals);
            if (mean < meanMin || mean > meanMax) return false;

            double sd = stddev(intervals, mean);
            return sd <= stddevMax;
        }

        void reset() { times.clear(); }

        private static long[] toIntervals(Deque<Long> ts) {
            long[] out = new long[Math.max(0, ts.size() - 1)];
            if (out.length == 0) return out;
            Iterator<Long> it = ts.iterator();
            long prev = it.next();
            int i = 0;
            while (it.hasNext()) {
                long t = it.next();
                out[i++] = Math.max(0, t - prev);
                prev = t;
            }
            return out;
        }

        private static double mean(long[] a) {
            long sum = 0L;
            for (long v : a) sum += v;
            return sum / (double) a.length;
        }

        private static double stddev(long[] a, double mean) {
            if (a.length == 0) return 0.0;
            double acc = 0.0;
            for (long v : a) {
                double d = v - mean;
                acc += d * d;
            }
            return Math.sqrt(acc / a.length);
        }
    }
}
