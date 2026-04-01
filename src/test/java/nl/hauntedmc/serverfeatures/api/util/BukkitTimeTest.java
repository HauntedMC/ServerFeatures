package nl.hauntedmc.serverfeatures.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BukkitTimeTest {

    @Test
    void conversionFactoriesClampAndRoundAsExpected() {
        assertEquals(0L, BukkitTime.ticks(-5).toTicks());
        assertEquals(1L, BukkitTime.milliseconds(1).toTicks());
        assertEquals(2L, BukkitTime.milliseconds(51).toTicks());
        assertEquals(40L, BukkitTime.seconds(2).toTicks());
        assertEquals(20L, BukkitTime.seconds(1.0).toTicks());
        assertEquals(1L, BukkitTime.seconds(0.001).toTicks());
        assertEquals(1200L, BukkitTime.minutes(1).toTicks());
        assertEquals(72000L, BukkitTime.hours(1).toTicks());
    }

    @Test
    void arithmeticOperationsUseExactMath() {
        assertEquals(30L, BukkitTime.ticks(10).plus(BukkitTime.ticks(20)).toTicks());
        assertEquals(60L, BukkitTime.ticks(20).multipliedBy(3).toTicks());
        assertEquals("60t", BukkitTime.ticks(60).toString());
    }

    @Test
    void overflowThrowsArithmeticException() {
        assertThrows(ArithmeticException.class, () -> BukkitTime.seconds(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> BukkitTime.ticks(Long.MAX_VALUE).plus(BukkitTime.ticks(1)));
        assertThrows(ArithmeticException.class, () -> BukkitTime.ticks(Long.MAX_VALUE).multipliedBy(2));
    }
}
