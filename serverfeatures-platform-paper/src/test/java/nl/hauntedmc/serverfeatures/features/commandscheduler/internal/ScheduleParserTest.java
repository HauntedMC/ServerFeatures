package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ExecutionMode;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleParser;
import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduleParserTest {

    @Test
    void parsesStrictTimeAndDutchDayAliases() {
        assertEquals(LocalTime.of(7, 5), ScheduleParser.parseTime("07:05"));
        assertEquals(DayOfWeek.MONDAY, ScheduleParser.parseDay("maandag"));
        assertEquals(DayOfWeek.FRIDAY, ScheduleParser.parseDay("vr"));
        assertEquals(ScheduleType.WEEKLY, ScheduleParser.parseType("weekly"));
        assertEquals(ExecutionMode.RANDOM, ScheduleParser.parseMode("random"));
    }

    @Test
    void rejectsAmbiguousOrOutOfRangeTimes() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleParser.parseTime("7:05"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleParser.parseTime("24:00"));
        assertThrows(IllegalArgumentException.class, () -> ScheduleParser.parseTime("12:60"));
    }
}
