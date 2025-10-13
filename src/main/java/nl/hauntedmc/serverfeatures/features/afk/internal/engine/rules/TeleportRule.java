package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;

public final class TeleportRule implements AfkRule {
    @Override
    public AfkDecision evaluate(AfkEvent event, AfkPlayerState state, AfkServiceFacade cfg) {
        if (event.type() != AfkEventType.TELEPORT) return AfkDecision.none();
        long now = event.timestamp();
        state.setLastMove(now);
        if (!state.isAfk() && !state.isSuspicious()) {
            return AfkDecision.of(AfkPriority.LOW, AfkDecisionType.TOUCH_ACTIVITY);
        }
        return AfkDecision.none();
    }
}
