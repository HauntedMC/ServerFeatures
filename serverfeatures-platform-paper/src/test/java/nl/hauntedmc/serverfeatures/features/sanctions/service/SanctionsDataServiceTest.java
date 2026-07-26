package nl.hauntedmc.serverfeatures.features.sanctions.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanctionsDataServiceTest {

    @Test
    void remainingFormatsPermanentAndTemporaryDurations() {
        SanctionsDataService service = new SanctionsDataService(null);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertEquals("permanent", service.remaining(now, null));
        assertEquals("0m", service.remaining(now, now.minusSeconds(10)));
        assertEquals("1m", service.remaining(now, now.plusSeconds(61)));
        assertEquals("1d 2h 3m", service.remaining(now, now.plusSeconds(86_400 + 7_200 + 180)));
    }

    @Test
    void sanitizeHandlesNullBlankControlCharsAndLengthLimit() {
        SanctionsDataService service = new SanctionsDataService(null);

        assertEquals("-", service.sanitize(null));
        assertEquals("-", service.sanitize("   "));
        assertEquals("-", service.sanitize("\u0000\u0001"));
        assertEquals("reason", service.sanitize("  re\u0000ason  "));

        String longText = "a".repeat(600);
        String out = service.sanitize(longText);
        assertEquals(512, out.length());
        assertTrue(out.chars().allMatch(ch -> ch == 'a'));
    }
}

