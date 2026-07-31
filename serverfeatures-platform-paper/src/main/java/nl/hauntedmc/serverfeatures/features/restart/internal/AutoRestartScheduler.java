package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class AutoRestartScheduler {

    private final Restart feature;
    private final RestartService service;
    private final String hhmm;
    private final ZoneId zone;
    private final AtomicLong scheduleToken = new AtomicLong(0L);

    private volatile ZonedDateTime nextRunAt;

    public AutoRestartScheduler(Restart feature, RestartService service, String hhmm) {
        this(feature, service, hhmm, ZoneId.systemDefault());
    }

    public AutoRestartScheduler(
            Restart feature,
            RestartService service,
            String hhmm,
            ZoneId zone
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.service = Objects.requireNonNull(service, "service");
        this.hhmm = hhmm;
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public void scheduleNext() {
        cancel();
        long token = scheduleToken.incrementAndGet();
        ZonedDateTime runAt = nextRunAt(ZonedDateTime.now(zone), hhmm);
        nextRunAt = runAt;

        long seconds = Math.max(1L, Duration.between(ZonedDateTime.now(zone), runAt).getSeconds());
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> {
            if (scheduleToken.get() != token) {
                return;
            }

            feature.getLogger().info("Automatic restart trigger reached (" + hhmm + ").");
            // Schedule tomorrow before starting today's operation. Cancelling or skipping today's
            // restart must never silently disable the recurring daily schedule.
            scheduleNext();
            service.startAutomatic();
        }, BukkitTime.ticks(seconds * 20L));

        feature.getLogger().info("Automatic restart scheduled for " + runAt + ".");
    }

    public void cancel() {
        scheduleToken.incrementAndGet();
        nextRunAt = null;
    }

    public ZonedDateTime getNextRunAt() {
        return nextRunAt;
    }

    ZonedDateTime nextRunAt(ZonedDateTime now, String raw) {
        try {
            LocalTime target = parseStrictHHmm(raw);
            ZonedDateTime runAt = now.with(target);
            if (!runAt.isAfter(now)) {
                runAt = runAt.plusDays(1);
            }
            return runAt;
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Invalid auto.time '" + raw
                            + "', expected HH:mm (00:00-23:59). Defaulting to 04:00."
            );
            return nextRunAt(now, "04:00");
        }
    }

    static LocalTime parseStrictHHmm(String raw) {
        String value = String.valueOf(raw).trim();
        if (!value.matches("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("Not HH:mm");
        }
        return LocalTime.of(
                Integer.parseInt(value.substring(0, 2)),
                Integer.parseInt(value.substring(3, 5))
        );
    }
}
