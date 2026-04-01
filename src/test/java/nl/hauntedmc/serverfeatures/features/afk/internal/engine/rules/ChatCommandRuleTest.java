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

class ChatCommandRuleTest {

    private final ChatCommandRule rule = new ChatCommandRule();

    @Test
    void chatAlwaysForcesActivityAndLeaveAfk() {
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.CHAT, 0L, null, null),
                new AfkPlayerState(),
                new RuleTestFacade()
        );

        assertEquals(AfkPriority.HIGH, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.CLEAR_SUSPICIOUS));
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void afkCommandIsIgnored() {
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.afkCommand = true;

        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.COMMAND, 0L, "/afk", null),
                new AfkPlayerState(),
                cfg
        );

        assertTrue(decision.isNoop());
    }

    @Test
    void nonAfkCommandTriggersStrongDecision() {
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.afkCommand = false;

        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.COMMAND, 0L, "/spawn", null),
                new AfkPlayerState(),
                cfg
        );

        assertEquals(AfkPriority.HIGH, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.CLEAR_SUSPICIOUS));
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void unrelatedEventReturnsNoop() {
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.MOVE, 0L, null, null),
                new AfkPlayerState(),
                new RuleTestFacade()
        );

        assertTrue(decision.isNoop());
    }
}

