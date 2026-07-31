package nl.hauntedmc.serverfeatures.features.restart.command;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

final class RestartScheduleParser {

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu")
    };

    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H.mm"),
            DateTimeFormatter.ofPattern("HH.mm")
    };

    private static final Map<String, DayOfWeek> DAY_ALIASES = Map.ofEntries(
            Map.entry("mon", DayOfWeek.MONDAY),
            Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("ma", DayOfWeek.MONDAY),
            Map.entry("maandag", DayOfWeek.MONDAY),
            Map.entry("tue", DayOfWeek.TUESDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("di", DayOfWeek.TUESDAY),
            Map.entry("dinsdag", DayOfWeek.TUESDAY),
            Map.entry("wed", DayOfWeek.WEDNESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY),
            Map.entry("wo", DayOfWeek.WEDNESDAY),
            Map.entry("woensdag", DayOfWeek.WEDNESDAY),
            Map.entry("thu", DayOfWeek.THURSDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("do", DayOfWeek.THURSDAY),
            Map.entry("donderdag", DayOfWeek.THURSDAY),
            Map.entry("fri", DayOfWeek.FRIDAY),
            Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("vr", DayOfWeek.FRIDAY),
            Map.entry("vrijdag", DayOfWeek.FRIDAY),
            Map.entry("sat", DayOfWeek.SATURDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("za", DayOfWeek.SATURDAY),
            Map.entry("zaterdag", DayOfWeek.SATURDAY),
            Map.entry("sun", DayOfWeek.SUNDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY),
            Map.entry("zo", DayOfWeek.SUNDAY),
            Map.entry("zondag", DayOfWeek.SUNDAY)
    );

    private RestartScheduleParser() {
    }

    static ZonedDateTime parse(String[] args, ZoneId zone, ZonedDateTime now) {
        if (args.length == 2) {
            return parseSingleTokenDateTime(args[1], zone);
        }

        if (args.length == 3) {
            String first = args[1];
            String second = args[2];
            LocalTime firstTime = tryParseTime(first);
            LocalTime secondTime = tryParseTime(second);

            if (firstTime != null && secondTime == null) {
                return combineDateOrDayWithTime(second, firstTime, zone, now);
            }
            if (secondTime != null && firstTime == null) {
                return combineDateOrDayWithTime(first, secondTime, zone, now);
            }
            return null;
        }

        return null;
    }

    private static ZonedDateTime parseSingleTokenDateTime(String token, ZoneId zone) {
        if (token == null) return null;

        try {
            return LocalDateTime.parse(token, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone);
        } catch (DateTimeParseException ignored) {
        }

        int separator = token.indexOf('T');
        if (separator > 0 && separator < token.length() - 1) {
            LocalDate date = tryParseDate(token.substring(0, separator));
            LocalTime time = tryParseTime(token.substring(separator + 1));
            if (date != null && time != null) {
                return ZonedDateTime.of(date, time, zone);
            }
        }
        return null;
    }

    private static ZonedDateTime combineDateOrDayWithTime(String token,
                                                          LocalTime time,
                                                          ZoneId zone,
                                                          ZonedDateTime now) {
        LocalDate date = tryParseDate(token);
        if (date != null) {
            return ZonedDateTime.of(date, time, zone);
        }

        DayOfWeek day = tryParseDayOfWeek(token);
        if (day == null) return null;

        ZonedDateTime candidate = now.withHour(time.getHour())
                .withMinute(time.getMinute())
                .withSecond(0)
                .withNano(0);
        int delta = (day.getValue() - candidate.getDayOfWeek().getValue() + 7) % 7;
        candidate = candidate.plusDays(delta);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    private static LocalDate tryParseDate(String token) {
        if (token == null) return null;
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(token, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static LocalTime tryParseTime(String token) {
        if (token == null) return null;
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                return LocalTime.parse(token, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static DayOfWeek tryParseDayOfWeek(String token) {
        if (token == null) return null;
        return DAY_ALIASES.get(token.toLowerCase(Locale.ROOT));
    }
}
