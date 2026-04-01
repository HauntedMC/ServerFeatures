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
        double horizontal2 = dx * dx + dz * dz;

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
                AfkAntiPattern.track(now, state, cfg);
                if (AfkAntiPattern.isSuspicious(state, cfg)) {
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
                && !state.isSuspicious() && !state.isAfkLocked(now)) {
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

}
