package nl.hauntedmc.serverfeatures.features.commandscheduler.internal;

import nl.hauntedmc.serverfeatures.features.commandscheduler.model.ScheduleTrigger;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleTimeCalculatorTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    @Test
    void dailyTriggerUsesTodayWhenTimeIsStillInTheFuture() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 5, 19, 0, 0, 0, AMSTERDAM);

        assertEquals(
                ZonedDateTime.of(2026, 8, 5, 20, 0, 0, 0, AMSTERDAM),
                ScheduleTimeCalculator.nextOccurrence(
                        ScheduleTrigger.daily(LocalTime.of(20, 0)),
                        now,
                        AMSTERDAM
                )
        );
    }

    @Test
    void dailyTriggerRollsAnElapsedOrEqualTimeToTomorrow() {
        ZonedDateTime atTarget = ZonedDateTime.of(2026, 8, 5, 20, 0, 0, 0, AMSTERDAM);

        assertEquals(
                atTarget.plusDays(1),
                ScheduleTimeCalculator.nextOccurrence(
                        ScheduleTrigger.daily(LocalTime.of(20, 0)),
                        atTarget,
                        AMSTERDAM
                )
        );
    }

    @Test
    void weeklyTriggerRollsPassedSameDayTimeToNextWeek() {
        ZonedDateTime fridayAfterTarget = ZonedDateTime.of(
                2026,
                8,
                7,
                22,
                0,
                0,
                0,
                AMSTERDAM
        );

        assertEquals(
                ZonedDateTime.of(2026, 8, 14, 21, 0, 0, 0, AMSTERDAM),
                ScheduleTimeCalculator.nextOccurrence(
                        ScheduleTrigger.weekly(DayOfWeek.FRIDAY, LocalTime.of(21, 0)),
                        fridayAfterTarget,
                        AMSTERDAM
                )
        );
    }

    @Test
    void nonexistentDstLocalTimeIsSkippedInsteadOfShifted() {
        ZonedDateTime beforeGap = ZonedDateTime.of(
                2026,
                3,
                28,
                3,
                0,
                0,
                0,
                AMSTERDAM
        );

        assertEquals(
                ZonedDateTime.of(2026, 3, 30, 2, 30, 0, 0, AMSTERDAM),
                ScheduleTimeCalculator.nextOccurrence(
                        ScheduleTrigger.daily(LocalTime.of(2, 30)),
                        beforeGap,
                        AMSTERDAM
                )
        );
    }

    @Test
    void overlappingDstLocalTimeUsesEarlierOffsetOnly() {
        ZonedDateTime beforeOverlap = ZonedDateTime.of(
                2026,
                10,
                24,
                3,
                0,
                0,
                0,
                AMSTERDAM
        );

        ZonedDateTime next = ScheduleTimeCalculator.nextOccurrence(
                ScheduleTrigger.daily(LocalTime.of(2, 30)),
                beforeOverlap,
                AMSTERDAM
        );

        assertEquals(ZoneOffset.ofHours(2), next.getOffset());
        assertEquals(2, next.getHour());
        assertEquals(30, next.getMinute());
    }

    @Test
    void overlapDoesNotRunAgainAtTheLaterOffset() {
        ZonedDateTime afterEarlierOccurrence = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 10, 25, 2, 15),
                AMSTERDAM,
                ZoneOffset.ofHours(1)
        );

        assertEquals(
                ZonedDateTime.of(2026, 10, 26, 2, 30, 0, 0, AMSTERDAM),
                ScheduleTimeCalculator.nextOccurrence(
                        ScheduleTrigger.daily(LocalTime.of(2, 30)),
                        afterEarlierOccurrence,
                        AMSTERDAM
                )
        );
    }
}
