package nl.hauntedmc.serverfeatures.features.restart.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RestartService {

    private static final long TICK_MS = 50L;

    private final Restart feature;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final AtomicLong sequenceToken = new AtomicLong(0);

    private final Title.Times titleTimes;
    private final int waitAfterNowSeconds;
    private final List<Integer> scheduleDesc;

    public RestartService(Restart feature) {
        this.feature = feature;

        int fadeInTicks  = feature.getInt("title_fade_in", 20);
        int stayTicks    = feature.getInt("title_stay", 100);
        int fadeOutTicks = feature.getInt("title_fade_out", 20);
        this.titleTimes = Title.Times.times(
                Duration.ofMillis(fadeInTicks * TICK_MS),
                Duration.ofMillis(stayTicks * TICK_MS),
                Duration.ofMillis(fadeOutTicks * TICK_MS)
        );

        this.waitAfterNowSeconds = feature.getInt("auto.wait_after_now_seconds", 5);
        this.scheduleDesc = parseSchedule();
    }

    public void forceImmediate(CommandSender initiator) {
        feature.getLogger().warning("Forced restart initiated by " + (initiator == null ? "system" : initiator.getName()));
        cancelIfRunning();
        saveKickShutdown();
    }

    public boolean startCommanded(CommandSender initiator) {
        if (!inProgress.compareAndSet(false, true)) {
            return false;
        }
        feature.getLogger().info("Restart initiated by " + (initiator == null ? "system" : initiator.getName()));
        runSequence();
        return true;
    }

    public void startAutomatic() {
        if (!inProgress.compareAndSet(false, true)) {
            feature.getLogger().warning("Automatic restart skipped; another restart is in progress.");
            return;
        }
        feature.getLogger().info("Automatic daily restart starting…");
        runSequence();
    }

    public void cancelIfRunning() {
        sequenceToken.incrementAndGet();
        inProgress.set(false);
    }

    private void runSequence() {
        final long token = sequenceToken.incrementAndGet();

        int first = scheduleDesc.getFirst();
        int last  = scheduleDesc.getLast();

        announceRemaining(first);

        for (int i = 1; i < scheduleDesc.size(); i++) {
            int remaining = scheduleDesc.get(i);
            int when = Math.max(0, first - remaining);
            scheduleInSeconds(when, () -> {
                if (!isTokenValid(token)) return;
                announceRemaining(remaining);
            });
        }

        int totalUntilZero = Math.max(0, first - last);
        scheduleInSeconds(totalUntilZero + waitAfterNowSeconds, () -> {
            if (!isTokenValid(token)) return;
            saveKickShutdown();
        });
    }

    private boolean isTokenValid(long token) {
        return sequenceToken.get() == token;
    }

    private void announceRemaining(int remainingSeconds) {
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
                title = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.title")
                        .forAudience(p)
                        .with("readable", t.readable())
                        .build();
                sub   = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.subtitle")
                        .forAudience(p)
                        .with("readable", t.readable())
                        .build();
                chat  = feature.getLocalizationHandler()
                        .getMessage("restart.countdown.chat")
                        .forAudience(p)
                        .with("readable", t.readable())
                        .build();
            }

            p.showTitle(Title.title(title, sub, titleTimes));
            p.sendMessage(chat);
        }
    }

    private void saveKickShutdown() {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            try {
                feature.getPlugin().getServer().dispatchCommand(
                        feature.getPlugin().getServer().getConsoleSender(), "save-all flush");
            } catch (Throwable t) {
                feature.getLogger().warning("Failed to dispatch 'save-all flush': " + t.getMessage());
            }
        });

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

    private List<Integer> parseSchedule() {
        Object raw = feature.getConfigHandler().getSetting("announce.schedule");
        List<Integer> out = new ArrayList<>();

        List<?> items = (raw instanceof List<?> l) ? l : List.of(60, 30, 0);
        for (Object o : items) {
            if (o instanceof Number n) {
                int v = n.intValue();
                if (v >= 0) out.add(v);
            } else if (o != null) {
                try {
                    int v = Integer.parseInt(String.valueOf(o).trim());
                    if (v >= 0) out.add(v);
                } catch (NumberFormatException ignored) {}
            }
        }

        out.sort(Comparator.reverseOrder());
        List<Integer> uniqueDesc = new ArrayList<>();
        Integer last = null;
        for (Integer v : out) {
            if (!Objects.equals(last, v)) uniqueDesc.add(v);
            last = v;
        }

        if (uniqueDesc.isEmpty() || uniqueDesc.getLast() != 0) {
            uniqueDesc.add(0);
        }

        return uniqueDesc;
    }

    private record TimeFmt(int m, int s, String mm, String ss, String readable) {
        static TimeFmt of(int totalSeconds) {
            int m = Math.max(0, totalSeconds) / 60;
            int s = Math.max(0, totalSeconds) % 60;
            String mm = String.format("%02d", m);
            String ss = String.format("%02d", s);
            String readable = (m > 0 && s > 0) ? (m + "m " + s + "s") : (m > 0 ? (m + "m") : (s + "s"));
            return new TimeFmt(m, s, mm, ss, readable);
        }
    }
}
