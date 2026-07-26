package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureTaskManagerTest {

    @Test
    void clampDelayNeverReturnsNegative() {
        assertEquals(0L, FeatureTaskManager.clampDelay(BukkitTime.ticks(-5)));
        assertEquals(3L, FeatureTaskManager.clampDelay(BukkitTime.ticks(3)));
    }

    @Test
    void clampPeriodHasMinimumOneTick() {
        assertEquals(1L, FeatureTaskManager.clampPeriod(BukkitTime.ticks(0)));
        assertEquals(1L, FeatureTaskManager.clampPeriod(BukkitTime.ticks(-2)));
        assertEquals(4L, FeatureTaskManager.clampPeriod(BukkitTime.ticks(4)));
    }
}
