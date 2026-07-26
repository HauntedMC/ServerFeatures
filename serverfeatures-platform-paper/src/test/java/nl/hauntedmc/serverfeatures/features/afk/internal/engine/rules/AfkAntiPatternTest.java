package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkAntiPatternTest {

    @Test
    void intervalsClampNegativeStepsToZero() {
        Deque<Long> timestamps = new ArrayDeque<>(List.of(100L, 90L, 130L));

        long[] intervals = AfkAntiPattern.intervals(timestamps);

        assertArrayEquals(new long[]{0L, 40L}, intervals);
    }

    @Test
    void meanAndStddevAreComputedCorrectly() {
        long[] values = {10L, 20L, 30L};

        assertEquals(20.0D, AfkAntiPattern.mean(values));
        assertEquals(Math.sqrt(200D / 3D), AfkAntiPattern.stddev(values, 20.0D));
    }

    @Test
    void trackPrunesSamplesOutsideWindow() {
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.antiWindowMs = 1_000L;
        AfkPlayerState state = new AfkPlayerState();

        AfkAntiPattern.track(1_000L, state, cfg);
        AfkAntiPattern.track(1_500L, state, cfg);
        AfkAntiPattern.track(2_500L, state, cfg);

        assertEquals(List.of(1_500L, 2_500L), List.copyOf(state.antiTimes()));
    }

    @Test
    void isSuspiciousRequiresEnoughSamplesAndMatchingStats() {
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.antiMinSamples = 3;
        cfg.antiMeanMinMs = 900L;
        cfg.antiMeanMaxMs = 1_100L;
        cfg.antiStddevMaxMs = 50L;

        AfkPlayerState state = new AfkPlayerState();
        state.antiTimes().addAll(List.of(1_000L, 2_000L, 3_000L, 4_000L));
        assertTrue(AfkAntiPattern.isSuspicious(state, cfg));

        AfkPlayerState tooFew = new AfkPlayerState();
        tooFew.antiTimes().addAll(List.of(1_000L, 2_000L, 3_000L));
        assertFalse(AfkAntiPattern.isSuspicious(tooFew, cfg));

        AfkPlayerState noisy = new AfkPlayerState();
        noisy.antiTimes().addAll(List.of(1_000L, 2_000L, 3_500L, 4_000L));
        assertFalse(AfkAntiPattern.isSuspicious(noisy, cfg));
    }
}

