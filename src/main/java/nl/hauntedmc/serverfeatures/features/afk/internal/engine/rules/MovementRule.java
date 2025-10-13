package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.util.Movement;

public final class MovementRule implements AfkRule {
    @Override
    public AfkDecision evaluate(AfkEvent event, AfkPlayerState state, AfkServiceFacade cfg) {
        if (event.type() != AfkEventType.MOVE || event.movement() == null) return AfkDecision.none();

        Movement m = event.movement();
        double dx = m.tx() - m.fx();
        double dz = m.tz() - m.fz();
        double dy = m.ty() - m.fy();
        double horizontal2 = dx*dx + dz*dz;

        double moveThresh = cfg.moveThreshold();
        boolean movedHoriz = horizontal2 >= (moveThresh * moveThresh);

        float dyaw = angDiff(m.tyaw(), m.fyaw());
        float dpitch = angDiff(m.tpitch(), m.fpitch());
        float rotThresh = cfg.rotateThreshold();
        boolean rotated = dyaw >= rotThresh || dpitch >= rotThresh;

        boolean verticalOnly = !movedHoriz && Math.abs(dy) > cfg.verticalEpsilon();

        long now = event.timestamp();

        if (movedHoriz || rotated) state.setLastMove(now);

        if (cfg.antiEnabled()) {
            if (verticalOnly) {
                trackAnti(now, state, cfg);
                if (isSuspicious(state, cfg)) {
                    return AfkDecision.of(AfkPriority.HIGH, AfkDecisionType.FLAG_SUSPICIOUS);
                }
            }
        }

        if (!state.isAfk()) {
            if (state.isSuspicious()) return AfkDecision.none();
            if (movedHoriz || rotated) {
                return AfkDecision.of(AfkPriority.LOW, AfkDecisionType.TOUCH_ACTIVITY);
            }
            return AfkDecision.none();
        }

        long la = state.lastAux();
        if ((movedHoriz || rotated) && la > 0 && within(now, la, cfg.comboWindowMs())
                && !state.isSuspicious() && !state.isAfkLocked(now)) { // NEW
            return AfkDecision.of(AfkPriority.MEDIUM, AfkDecisionType.LEAVE_AFK);
        }

        return AfkDecision.none();
    }

    private static float angDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
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
            if (prev == Long.MIN_VALUE) { prev = t; continue; }
            out[i++] = Math.max(0, t - prev);
            prev = t;
        }
        return out;
    }

    private static double mean(long[] a) {
        long sum = 0; for (long v : a) sum += v; return sum / (double) a.length;
    }

    private static double stddev(long[] a, double mean) {
        if (a.length == 0) return 0;
        double acc = 0; for (long v : a) { double d = v - mean; acc += d * d; }
        return Math.sqrt(acc / a.length);
    }
}
