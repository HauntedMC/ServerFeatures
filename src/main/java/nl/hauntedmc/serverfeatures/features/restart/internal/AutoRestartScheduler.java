package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

public class AutoRestartScheduler {

    private final Restart feature;
    private final RestartService service;
    private final String hhmm;

    private final AtomicLong scheduleToken = new AtomicLong(0);

    public AutoRestartScheduler(Restart feature, RestartService service, String hhmm) {
        this.feature = feature;
        this.service = service;
        this.hhmm = hhmm;
    }

    public void scheduleNext() {
        cancel();
        long token = scheduleToken.incrementAndGet();

        long ticksDelay = computeDelayTicks(hhmm);
        feature.getLifecycleManager().getTaskManager()
                .scheduleDelayedTask(() -> {
                    if (scheduleToken.get() != token) return;
                    feature.getLogger().info("Automatic restart trigger reached (" + hhmm + ").");
                    service.startAutomatic();
                }, BukkitTime.ticks(ticksDelay));

        feature.getLogger().info("Automatic restart scheduled for " + nextRunHuman(hhmm) + ".");
    }

    public void cancel() {
        scheduleToken.incrementAndGet();
    }

    private long computeDelayTicks(String raw) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime runAt = nextRunAt(now, raw);
        long seconds = Duration.between(now, runAt).getSeconds();
        return Math.max(1, seconds) * 20L;
    }

    private String nextRunHuman(String raw) {
        ZonedDateTime runAt = nextRunAt(ZonedDateTime.now(ZoneId.systemDefault()), raw);
        return runAt.toString();
    }

    private ZonedDateTime nextRunAt(ZonedDateTime now, String raw) {
        try {
            LocalTime target = parseStrictHHmm(raw);
            ZonedDateTime runAt = now.with(target);
            if (!runAt.isAfter(now)) runAt = runAt.plusDays(1);
            return runAt;
        } catch (Throwable t) {
            feature.getLogger().warning("Invalid auto.time '" + raw + "', expected HH:mm (00:00–23:59). Defaulting to 04:00.");
            return nextRunAt(now, "04:00");
        }
    }

    private static LocalTime parseStrictHHmm(String raw) {
        String s = String.valueOf(raw).trim();
        // Only accept exactly HH:mm where HH=00..23 and mm=00..59
        if (!s.matches("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("Not HH:mm");
        }
        int h = Integer.parseInt(s.substring(0, 2));
        int m = Integer.parseInt(s.substring(3, 5));
        return LocalTime.of(h, m);
    }
}
