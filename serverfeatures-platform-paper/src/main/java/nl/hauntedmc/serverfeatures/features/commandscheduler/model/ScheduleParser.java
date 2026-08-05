package nl.hauntedmc.serverfeatures.features.commandscheduler.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;

public final class ScheduleParser {

    private static final Map<String, DayOfWeek> DAY_ALIASES = Map.ofEntries(
            Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("mon", DayOfWeek.MONDAY),
            Map.entry("maandag", DayOfWeek.MONDAY),
            Map.entry("ma", DayOfWeek.MONDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("tue", DayOfWeek.TUESDAY),
            Map.entry("dinsdag", DayOfWeek.TUESDAY),
            Map.entry("di", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY),
            Map.entry("wed", DayOfWeek.WEDNESDAY),
            Map.entry("woensdag", DayOfWeek.WEDNESDAY),
            Map.entry("wo", DayOfWeek.WEDNESDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("thu", DayOfWeek.THURSDAY),
            Map.entry("donderdag", DayOfWeek.THURSDAY),
            Map.entry("do", DayOfWeek.THURSDAY),
            Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("fri", DayOfWeek.FRIDAY),
            Map.entry("vrijdag", DayOfWeek.FRIDAY),
            Map.entry("vr", DayOfWeek.FRIDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("sat", DayOfWeek.SATURDAY),
            Map.entry("zaterdag", DayOfWeek.SATURDAY),
            Map.entry("za", DayOfWeek.SATURDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY),
            Map.entry("sun", DayOfWeek.SUNDAY),
            Map.entry("zondag", DayOfWeek.SUNDAY),
            Map.entry("zo", DayOfWeek.SUNDAY)
    );

    private ScheduleParser() {
    }

    public static LocalTime parseTime(String raw) {
        String value = String.valueOf(raw).trim();
        if (!value.matches("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("Expected HH:mm (00:00-23:59)");
        }
        return LocalTime.of(
                Integer.parseInt(value.substring(0, 2)),
                Integer.parseInt(value.substring(3, 5))
        );
    }

    public static DayOfWeek parseDay(String raw) {
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        DayOfWeek day = DAY_ALIASES.get(value);
        if (day == null) {
            try {
                day = DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Unknown day: " + raw);
            }
        }
        return day;
    }

    public static ScheduleType parseType(String raw) {
        try {
            return ScheduleType.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Expected daily or weekly", exception);
        }
    }

    public static ExecutionMode parseMode(String raw) {
        try {
            return ExecutionMode.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Expected sequence or random", exception);
        }
    }
}
