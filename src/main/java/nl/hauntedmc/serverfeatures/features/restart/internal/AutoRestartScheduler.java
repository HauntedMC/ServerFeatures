package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;

import java.time.*;
import java.time.format.DateTimeFormatter;
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
                    if (scheduleToken.get() != token) return; // canceled/replaced
                    feature.getLogger().info("Automatic restart trigger reached (" + hhmm + ").");
                    service.startAutomatic();
                }, BukkitTime.ticks(ticksDelay));

        feature.getLogger().info("Automatic restart scheduled for " + nextRunHuman(hhmm) + ".");
    }

    public void cancel() {
        scheduleToken.incrementAndGet();
    }

    private long computeDelayTicks(String hhmm) {
        try {
            LocalTime target = LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("H:mm"));
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime runAt = now.with(target);
            if (!runAt.isAfter(now)) runAt = runAt.plusDays(1);
            long seconds = Duration.between(now, runAt).getSeconds();
            return Math.max(1, seconds) * 20L;
        } catch (Throwable t) {
            feature.getLogger().warning("Invalid auto.time '" + hhmm + "', defaulting to 04:00.");
            return computeDelayTicks("04:00");
        }
    }

    private String nextRunHuman(String hhmm) {
        try {
            LocalTime target = LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("H:mm"));
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime runAt = now.with(target);
            if (!runAt.isAfter(now)) runAt = runAt.plusDays(1);
            return runAt.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
