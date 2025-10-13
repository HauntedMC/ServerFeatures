package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.AfkServiceFacade;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;

public final class ChatCommandRule implements AfkRule {
    @Override
    public AfkDecision evaluate(AfkEvent event, AfkPlayerState state, AfkServiceFacade cfg) {
        if (event.type() == AfkEventType.CHAT) {
            return AfkDecision.of(AfkPriority.HIGH,
                    AfkDecisionType.CLEAR_SUSPICIOUS,
                    AfkDecisionType.LEAVE_AFK,
                    AfkDecisionType.TOUCH_ACTIVITY);
        }
        if (event.type() == AfkEventType.COMMAND) {
            if (cfg.isAfkCommand(event.payload())) return AfkDecision.none();
            return AfkDecision.of(AfkPriority.HIGH,
                    AfkDecisionType.CLEAR_SUSPICIOUS,
                    AfkDecisionType.LEAVE_AFK,
                    AfkDecisionType.TOUCH_ACTIVITY);
        }
        return AfkDecision.none();
    }
}
