package nl.hauntedmc.serverfeatures.features.restart.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RestartService {

    private final Restart feature;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final AtomicLong sequenceToken = new AtomicLong(0);

    public RestartService(Restart feature) {
        this.feature = feature;
    }

    public boolean startCommanded(CommandSender initiator) {
        if (!inProgress.compareAndSet(false, true)) {
            return false;
        }
        feature.getLogger().info("Restart initiated by " + (initiator == null ? "system" : initiator.getName()));
        runSequenceFromConfig();
        return true;
    }

    public void startAutomatic() {
        if (!inProgress.compareAndSet(false, true)) {
            feature.getLogger().warning("Automatic restart skipped; another restart is in progress.");
            return;
        }
        feature.getLogger().info("Automatic daily restart starting…");
        runSequenceFromConfig();
    }

    public void cancelIfRunning() {
        sequenceToken.incrementAndGet(); // invalidate all pending steps
        inProgress.set(false);
    }

    /* ---------------- sequence ---------------- */

    private void runSequenceFromConfig() {
        final long token = sequenceToken.incrementAndGet();

        final int fadeIn = feature.getInt("title_fade_in", 20);
        final int stay = feature.getInt("title_stay", 100);
        final int fadeOut = feature.getInt("title_fade_out", 20);
        final int waitAfterNow = feature.getInt("auto.wait_after_now_seconds", 5);

        // Parse schedule (seconds remaining), sanitize, ensure "0" exists
        List<Integer> schedule = parseSchedule();
        if (schedule.isEmpty() || schedule.getLast() != 0) {
            schedule.add(0);
        }

        // We announce immediately at the first (largest) remaining time.
        // Then schedule subsequent announcements after the delta from previous entry.
        // Example: [120,60,30,0] => t=0 (announce 120), +60s (60), +30s (30), +30s (0), then +waitAfterNow => shutdown.
        int prev = schedule.getFirst();
        announceRemaining(prev, fadeIn, stay, fadeOut, waitAfterNow);
        for (int i = 1; i < schedule.size(); i++) {
            int remaining = schedule.get(i);
            int delta = Math.max(0, prev - remaining);
            scheduleInSeconds(delta, () -> {
                if (!isTokenValid(token)) return;
                announceRemaining(remaining, fadeIn, stay, fadeOut, waitAfterNow);
            });
            prev = remaining;
        }

        // After the final (remaining == 0) announcement, wait waitAfterNow seconds then save/kick/shutdown.
        scheduleInSeconds(schedule.getFirst() - schedule.getLast() + waitAfterNow, () -> {
            if (!isTokenValid(token)) return;
            saveKickShutdown();
        });
    }

    private boolean isTokenValid(long token) {
        return sequenceToken.get() == token;
    }

    private List<Integer> parseSchedule() {
        Object raw = feature.getConfigHandler().getSetting("announce.schedule");
        List<Integer> out = new ArrayList<>();
        try {
            if (raw instanceof Collection<?> c) {
                for (Object o : c) pushIfValid(out, o);
            } else if (raw != null) {
                String s = String.valueOf(raw);
                for (String part : s.split(",")) pushIfValid(out, part.trim());
            }
        } catch (Throwable ignored) {
        }
        // sort descending (largest remaining first), unique, non-negative
        out.removeIf(v -> v == null || v < 0);
        out.sort(Comparator.reverseOrder());
        // de-dup
        List<Integer> unique = new ArrayList<>();
        Integer last = null;
        for (Integer v : out) {
            if (!Objects.equals(last, v)) unique.add(v);
            last = v;
        }
        return unique;
    }

    private void pushIfValid(List<Integer> out, Object o) {
        try {
            int v = (o instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(o));
            if (v >= 0) out.add(v);
        } catch (Throwable ignored) {
        }
    }

    private void announceRemaining(int remainingSeconds,
                                   int fadeInTicks, int stayTicks, int fadeOutTicks,
                                   int waitAfterNow) {

        // Convert ticks (config) to durations for Adventure Title.Times
        Duration in = Duration.ofSeconds(Math.max(0, fadeInTicks) / 20);
        Duration st = Duration.ofSeconds(Math.max(0, stayTicks) / 20);
        Duration out = Duration.ofSeconds(Math.max(0, fadeOutTicks) / 20);

        // Precompute formatted placeholders
        TimeFmt t = TimeFmt.of(remainingSeconds);

        for (Player p : feature.getPlugin().getServer().getOnlinePlayers()) {
            Component title;
            Component sub;
            Component chat;

            if (remainingSeconds == 0) {
                title = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.now.title")
                        .forAudience(p)
                        .build();
                sub   = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.now.subtitle")
                        .forAudience(p)
                        .build();
                chat  = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.now.chat")
                        .forAudience(p)
                        .build();
            } else {
                // Generic countdown — supply {mm},{ss},{m},{s},{readable}
                Map<String,String> ph = Map.of(
                        "mm", t.mm,
                        "ss", t.ss,
                        "m",  String.valueOf(t.m),
                        "s",  String.valueOf(t.s),
                        "readable", t.readable
                );
                title = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.title")
                        .forAudience(p)
                        .withPlaceholders(ph)
                        .build();
                sub   = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.subtitle")
                        .forAudience(p)
                        .withPlaceholders(ph)
                        .build();
                chat  = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.chat")
                        .forAudience(p)
                        .withPlaceholders(ph)
                        .build();
            }

            p.showTitle(Title.title(title, sub, Title.Times.times(in, st, out)));
            p.sendMessage(chat);
        }
    }


    private void saveKickShutdown() {
        // save-all flush (sync)
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            try {
                feature.getPlugin().getServer().dispatchCommand(
                        feature.getPlugin().getServer().getConsoleSender(), "save-all flush");
            } catch (Throwable t) {
                feature.getLogger().warning("Failed to dispatch 'save-all flush': " + t.getMessage());
            }
        });

        // Kick everyone (localized), then shutdown
        for (Player p : feature.getPlugin().getServer().getOnlinePlayers()) {
            Component kick = feature.getLocalizationHandler()
                    .getMessage("restart.kick")
                    .forAudience(p)
                    .build();
            try {
                p.kick(kick);
            } catch (Throwable ignored) {
            }
        }

        feature.getPlugin().getServer().shutdown();
    }

    private void scheduleInSeconds(int delaySeconds, Runnable action) {
        long ticks = Math.max(0, delaySeconds) * 20L;
        feature.getLifecycleManager().getTaskManager()
                .scheduleDelayedTask(action, BukkitTime.ticks(ticks));
    }

    /* --------------- tiny formatter helper --------------- */

    private static final class TimeFmt {
        final int m, s;
        final String mm, ss, readable;

        private TimeFmt(int m, int s) {
            this.m = m;
            this.s = s;
            this.mm = String.format("%02d", m);
            this.ss = String.format("%02d", s);
            if (m > 0 && s > 0) {
                this.readable = m + "m " + s + "s";
            } else if (m > 0) {
                this.readable = m + "m";
            } else {
                this.readable = s + "s";
            }
        }

        static TimeFmt of(int totalSeconds) {
            int m = Math.max(0, totalSeconds) / 60;
            int s = Math.max(0, totalSeconds) % 60;
            return new TimeFmt(m, s);
        }
    }
}
