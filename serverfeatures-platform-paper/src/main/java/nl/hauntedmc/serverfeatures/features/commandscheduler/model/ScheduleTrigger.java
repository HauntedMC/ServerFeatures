package nl.hauntedmc.serverfeatures.features.commandscheduler.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public record ScheduleTrigger(ScheduleType type, DayOfWeek day, LocalTime time) {

    public ScheduleTrigger {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(time, "time");
        if (type == ScheduleType.DAILY && day != null) {
            throw new IllegalArgumentException("Daily triggers must not define a day");
        }
        if (type == ScheduleType.WEEKLY && day == null) {
            throw new IllegalArgumentException("Weekly triggers require a day");
        }
    }

    public static ScheduleTrigger daily(LocalTime time) {
        return new ScheduleTrigger(ScheduleType.DAILY, null, time);
    }

    public static ScheduleTrigger weekly(DayOfWeek day, LocalTime time) {
        return new ScheduleTrigger(ScheduleType.WEEKLY, day, time);
    }
}
