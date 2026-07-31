package nl.hauntedmc.serverfeatures.features.restart.command;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RestartScheduleParserTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
    private static final ZonedDateTime NOW = ZonedDateTime.of(
            2026,
            7,
            31,
            9,
            30,
            0,
            0,
            ZONE
    );

    @Test
    void parsesIsoDateTimeToken() {
        assertEquals(
                ZonedDateTime.of(2026, 8, 1, 5, 15, 0, 0, ZONE),
                RestartScheduleParser.parse(
                        new String[]{"schedule", "2026-08-01T05:15"},
                        ZONE,
                        NOW
                )
        );
    }

    @Test
    void parsesSupportedDateAndTimeFormatsInEitherOrder() {
        ZonedDateTime expected = ZonedDateTime.of(2026, 8, 2, 4, 30, 0, 0, ZONE);

        assertEquals(
                expected,
                RestartScheduleParser.parse(
                        new String[]{"schedule", "02-08-2026", "04:30"},
                        ZONE,
                        NOW
                )
        );
        assertEquals(
                expected,
                RestartScheduleParser.parse(
                        new String[]{"schedule", "4.30", "02/08/2026"},
                        ZONE,
                        NOW
                )
        );
    }

    @Test
    void parsesEnglishAndDutchWeekdaysAndRollsPastTimeToNextWeek() {
        assertEquals(
                ZonedDateTime.of(2026, 7, 31, 12, 0, 0, 0, ZONE),
                RestartScheduleParser.parse(
                        new String[]{"schedule", "friday", "12:00"},
                        ZONE,
                        NOW
                )
        );
        assertEquals(
                ZonedDateTime.of(2026, 8, 7, 5, 0, 0, 0, ZONE),
                RestartScheduleParser.parse(
                        new String[]{"schedule", "vrijdag", "05:00"},
                        ZONE,
                        NOW
                )
        );
    }

    @Test
    void rejectsIncompleteOrInvalidSchedule() {
        assertNull(RestartScheduleParser.parse(
                new String[]{"schedule"},
                ZONE,
                NOW
        ));
        assertNull(RestartScheduleParser.parse(
                new String[]{"schedule", "tomorrow", "05:00"},
                ZONE,
                NOW
        ));
        assertNull(RestartScheduleParser.parse(
                new String[]{"schedule", "2026-08-01", "25:00"},
                ZONE,
                NOW
        ));
    }
}
