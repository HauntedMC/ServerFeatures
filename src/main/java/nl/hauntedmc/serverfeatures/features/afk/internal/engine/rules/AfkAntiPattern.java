package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;

final class AfkAntiPattern {

    private AfkAntiPattern() {
    }

    static void track(long now, AfkPlayerState state, AfkServiceFacade cfg) {
        state.antiTimes().addLast(now);
        while (!state.antiTimes().isEmpty() && now - state.antiTimes().peekFirst() > cfg.antiWindowMs()) {
            state.antiTimes().removeFirst();
        }
    }

    static boolean isSuspicious(AfkPlayerState state, AfkServiceFacade cfg) {
        var times = state.antiTimes();
        if (times.size() < cfg.antiMinSamples() + 1) return false;

        long[] intervals = intervals(times);
        if (intervals.length < cfg.antiMinSamples()) return false;

        double mean = mean(intervals);
        if (mean < cfg.antiMeanMinMs() || mean > cfg.antiMeanMaxMs()) return false;

        double stddev = stddev(intervals, mean);
        return stddev <= cfg.antiStddevMaxMs();
    }

    static long[] intervals(Iterable<Long> timestamps) {
        long previous = Long.MIN_VALUE;
        int count = 0;
        for (Long ignored : timestamps) count++;

        long[] out = new long[Math.max(0, count - 1)];
        int i = 0;
        for (Long t : timestamps) {
            if (previous == Long.MIN_VALUE) {
                previous = t;
                continue;
            }
            out[i++] = Math.max(0, t - previous);
            previous = t;
        }
        return out;
    }

    static double mean(long[] values) {
        long sum = 0;
        for (long v : values) sum += v;
        return sum / (double) values.length;
    }

    static double stddev(long[] values, double mean) {
        if (values.length == 0) return 0;
        double acc = 0;
        for (long v : values) {
            double d = v - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / values.length);
    }
}
