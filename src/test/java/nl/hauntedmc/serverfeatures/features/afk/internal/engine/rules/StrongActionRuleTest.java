package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongActionRuleTest {

    private final StrongActionRule rule = new StrongActionRule();

    @Test
    void strongActionReturnsHighPriorityActions() {
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.STRONG_ACTION, 0L, null, null),
                new AfkPlayerState(),
                new RuleTestFacade()
        );

        assertEquals(AfkPriority.HIGH, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.CLEAR_SUSPICIOUS));
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void nonStrongEventReturnsNoop() {
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.WEAK_ACTION, 0L, null, null),
                new AfkPlayerState(),
                new RuleTestFacade()
        );

        assertTrue(decision.isNoop());
    }
}

