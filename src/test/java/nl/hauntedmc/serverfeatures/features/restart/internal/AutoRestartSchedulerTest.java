package nl.hauntedmc.serverfeatures.features.restart.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
