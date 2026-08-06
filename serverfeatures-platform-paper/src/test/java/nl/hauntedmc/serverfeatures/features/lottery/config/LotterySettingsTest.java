package nl.hauntedmc.serverfeatures.features.lottery.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotterySettingsTest {

    @Test
    void intervalScheduleAdvancesFromCurrentTime() {
        LotterySettings.Schedule schedule = new LotterySettings.Schedule(
                LotterySettings.ScheduleMode.INTERVAL,
                Duration.ofHours(2),
                ZoneId.of("UTC"),
                List.of()
        );
        assertEquals(7_200_001L, schedule.nextCloseAt(1L));
    }

    @Test
    void fixedScheduleAlwaysReturnsAFutureTime() {
        LotterySettings.Schedule schedule = new LotterySettings.Schedule(
                LotterySettings.ScheduleMode.FIXED_TIMES,
                Duration.ofHours(1),
                ZoneId.of("Europe/Amsterdam"),
                List.of(LocalTime.of(4, 0), LocalTime.of(20, 0))
        );
        long now = 1_700_000_000_000L;
        assertTrue(schedule.nextCloseAt(now) > now);
    }
}
