package nl.hauntedmc.serverfeatures.features.backup.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupServiceTest {

    @Test
    void asIntUsesNumberStringOrDefault() {
        assertEquals(12, BackupService.asInt(12, 5));
        assertEquals(9, BackupService.asInt(" 9 ", 5));
        assertEquals(5, BackupService.asInt("bad", 5));
        assertEquals(5, BackupService.asInt(null, 5));
    }

    @Test
    void asStringUsesNonBlankOrDefault() {
        assertEquals("value", BackupService.asString("value", "def"));
        assertEquals("def", BackupService.asString("   ", "def"));
        assertEquals("def", BackupService.asString(null, "def"));
    }

    @Test
    void humanBytesFormatsSmallAndScaledValues() {
        assertEquals("512 B", BackupService.humanBytes(512L));
        assertEquals("1.0 KB", BackupService.humanBytes(1024L));
        assertEquals("1.5 KB", BackupService.humanBytes(1536L));
    }

    @Test
    void extractDateFindsEmbeddedDateInFilename() {
        Optional<?> valid = BackupService.extractDate("backup_01-02-2026_120000.zip");
        Optional<?> invalid = BackupService.extractDate("backup_no_date.zip");

        assertEquals(Optional.of(LocalDate.of(2026, 2, 1)), valid);
        assertEquals(Optional.empty(), invalid);
    }

    @Test
    void daysOldComputesWholeDayDelta() {
        LocalDate today = LocalDate.of(2026, 4, 1);

        assertEquals(0, BackupService.daysOld(today, today));
        assertEquals(10, BackupService.daysOld(today.minusDays(10), today));
    }
}
