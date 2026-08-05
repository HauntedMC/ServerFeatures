package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleType;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

public final class ScheduleTimeCalculator {

    private ScheduleTimeCalculator() {
    }

    public static ZonedDateTime nextOccurrence(
            ScheduleTrigger trigger,
            ZonedDateTime now,
            ZoneId zone
    ) {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");

        ZonedDateTime zonedNow = now.withZoneSameInstant(zone);
        return trigger.type() == ScheduleType.DAILY
                ? nextDaily(trigger, zonedNow, zone)
                : nextWeekly(trigger, zonedNow, zone);
    }

    private static ZonedDateTime nextDaily(
            ScheduleTrigger trigger,
            ZonedDateTime now,
            ZoneId zone
    ) {
        LocalDate start = now.toLocalDate();
        for (int offset = 0; offset <= 8; offset++) {
            ZonedDateTime candidate = resolve(start.plusDays(offset), trigger, zone);
            if (candidate != null && candidate.toInstant().isAfter(now.toInstant())) {
                return candidate;
            }
        }
        throw new DateTimeException("Could not resolve next daily occurrence");
    }

    private static ZonedDateTime nextWeekly(
            ScheduleTrigger trigger,
            ZonedDateTime now,
            ZoneId zone
    ) {
        DayOfWeek day = Objects.requireNonNull(trigger.day(), "weekly day");
        LocalDate first = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day));
        for (int weeks = 0; weeks <= 2; weeks++) {
            ZonedDateTime candidate = resolve(first.plusWeeks(weeks), trigger, zone);
            if (candidate != null && candidate.toInstant().isAfter(now.toInstant())) {
                return candidate;
            }
        }
        throw new DateTimeException("Could not resolve next weekly occurrence");
    }

    private static ZonedDateTime resolve(
            LocalDate date,
            ScheduleTrigger trigger,
            ZoneId zone
    ) {
        LocalDateTime localDateTime = LocalDateTime.of(date, trigger.time());
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(localDateTime);
        if (offsets.isEmpty()) {
            return null;
        }
        // During an autumn overlap, execute once at the earlier occurrence.
        return ZonedDateTime.ofLocal(localDateTime, zone, offsets.getFirst());
    }
}
