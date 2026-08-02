package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPickupWriteRevisionClockTest {

    @Test
    void rapidRequestsAreStrictlyMonotonic() {
        AutoPickupWriteRevisionClock clock = new AutoPickupWriteRevisionClock(7, () -> 1_000L);

        long first = clock.next();
        long second = clock.next();
        long third = clock.next();

        assertTrue(first < second);
        assertTrue(second < third);
        assertEquals(first + 1L, second);
        assertEquals(second + 1L, third);
    }

    @Test
    void observedDatabaseRevisionFencesTheNextLocalRequest() {
        AutoPickupWriteRevisionClock clock = new AutoPickupWriteRevisionClock(3, () -> 100L);
        long remoteRevision = 9_000_000L;

        clock.observe(remoteRevision);

        assertEquals(remoteRevision + 1L, clock.next());
    }

    @Test
    void laterWallTimeDominatesLocalSequence() {
        AtomicLong micros = new AtomicLong(10L);
        AutoPickupWriteRevisionClock clock = new AutoPickupWriteRevisionClock(1, micros::get);
        long first = clock.next();

        micros.set(20L);
        long later = clock.next();

        assertTrue(later > first);
        assertEquals(20L * 256L + 1L, later);
    }
}
