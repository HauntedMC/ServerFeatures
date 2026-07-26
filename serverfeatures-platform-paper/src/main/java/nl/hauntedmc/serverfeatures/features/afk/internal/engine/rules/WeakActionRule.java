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
            AfkAntiPattern.track(now, state, cfg);
            if (AfkAntiPattern.isSuspicious(state, cfg)) {
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

}
