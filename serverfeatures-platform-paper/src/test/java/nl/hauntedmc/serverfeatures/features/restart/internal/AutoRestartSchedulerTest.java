package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoRestartSchedulerTest {

    @Test
    void parseStrictHHmmAcceptsStrictValidValues() {
        assertEquals(LocalTime.of(0, 0), AutoRestartScheduler.parseStrictHHmm("00:00"));
        assertEquals(LocalTime.of(7, 5), AutoRestartScheduler.parseStrictHHmm(" 07:05 "));
        assertEquals(LocalTime.of(23, 59), AutoRestartScheduler.parseStrictHHmm("23:59"));
    }

    @Test
    void parseStrictHHmmRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> AutoRestartScheduler.parseStrictHHmm(null));
        assertThrows(IllegalArgumentException.class, () -> AutoRestartScheduler.parseStrictHHmm(""));
        assertThrows(IllegalArgumentException.class, () -> AutoRestartScheduler.parseStrictHHmm("7:05"));
        assertThrows(IllegalArgumentException.class, () -> AutoRestartScheduler.parseStrictHHmm("24:00"));
        assertThrows(IllegalArgumentException.class, () -> AutoRestartScheduler.parseStrictHHmm("12:60"));
        assertThrows(IllegalArgumentException.class, () -> AutoRestartScheduler.parseStrictHHmm("ab:cd"));
    }

    @Test
    void nextRunUsesConfiguredZoneAndRollsElapsedTimeToTomorrow() {
        ZoneId zone = ZoneId.of("Europe/Amsterdam");
        AutoRestartScheduler scheduler = scheduler(zone);
        ZonedDateTime beforeTarget = ZonedDateTime.of(
                2026,
                7,
                31,
                4,
                30,
                0,
                0,
                zone
        );
        ZonedDateTime atTarget = beforeTarget.withHour(5).withMinute(0);

        assertEquals(atTarget, scheduler.nextRunAt(beforeTarget, "05:00"));
        assertEquals(atTarget.plusDays(1), scheduler.nextRunAt(atTarget, "05:00"));
    }

    @Test
    void invalidConfiguredTimeFallsBackToFourInTheMorning() {
        ZoneId zone = ZoneId.of("Europe/Amsterdam");
        AutoRestartScheduler scheduler = scheduler(zone);
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 31, 9, 30, 0, 0, zone);

        assertEquals(
                ZonedDateTime.of(2026, 8, 1, 4, 0, 0, 0, zone),
                scheduler.nextRunAt(now, "invalid")
        );
    }

    private AutoRestartScheduler scheduler(ZoneId zone) {
        Restart feature = mock(Restart.class);
        when(feature.getLogger()).thenReturn(mock(FeatureLogger.class));
        return new AutoRestartScheduler(
                feature,
                mock(RestartService.class),
                "05:00",
                zone
        );
    }
}
