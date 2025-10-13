package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;

public final class WeakActionRule implements AfkRule {
    @Override
    public AfkDecision evaluate(AfkEvent event, AfkPlayerState state, AfkServiceFacade cfg) {
        if (event.type() != AfkEventType.WEAK_ACTION && event.type() != AfkEventType.JUMP)
            return AfkDecision.none();

        long now = event.timestamp();
        state.setLastAux(now);

        if (cfg.antiEnabled()) {
            trackAnti(now, state, cfg);
            if (isSuspicious(state, cfg)) {
                return AfkDecision.of(AfkPriority.HIGH, AfkDecisionType.FLAG_SUSPICIOUS);
            }
        }

        if (!state.isAfk()) return AfkDecision.none();

        long lm = state.lastMove();
        if (lm > 0 && within(now, lm, cfg.comboWindowMs()) && !state.isSuspicious()) {
            return AfkDecision.of(AfkPriority.MEDIUM, AfkDecisionType.LEAVE_AFK);
        }
        return AfkDecision.none();
    }

    private static boolean within(long a, long b, long window) {
        long dt = Math.abs(a - b);
        return dt >= 0 && dt <= window;
    }

    private static void trackAnti(long now, AfkPlayerState s, AfkServiceFacade cfg) {
        s.antiTimes().addLast(now);
        while (!s.antiTimes().isEmpty() && now - s.antiTimes().peekFirst() > cfg.antiWindowMs()) {
            s.antiTimes().removeFirst();
        }
    }

    private static boolean isSuspicious(AfkPlayerState s, AfkServiceFacade cfg) {
        var times = s.antiTimes();
        if (times.size() < cfg.antiMinSamples() + 1) return false;
        long[] itv = intervals(times);
        if (itv.length < cfg.antiMinSamples()) return false;
        double mean = mean(itv);
        if (mean < cfg.antiMeanMinMs() || mean > cfg.antiMeanMaxMs()) return false;
        double sd = stddev(itv, mean);
        return sd <= cfg.antiStddevMaxMs();
    }

    private static long[] intervals(Iterable<Long> ts) {
        long prev = Long.MIN_VALUE;
        int count = 0;
        for (Long ignored : ts) count++;
        long[] out = new long[Math.max(0, count - 1)];
        int i = 0;
        for (Long t : ts) {
            if (prev == Long.MIN_VALUE) {
                prev = t;
                continue;
            }
            out[i++] = Math.max(0, t - prev);
            prev = t;
        }
        return out;
    }

    private static double mean(long[] a) {
        long sum = 0;
        for (long v : a) sum += v;
        return sum / (double) a.length;
    }

    private static double stddev(long[] a, double mean) {
        if (a.length == 0) return 0;
        double acc = 0;
        for (long v : a) {
            double d = v - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / a.length);
    }
}
