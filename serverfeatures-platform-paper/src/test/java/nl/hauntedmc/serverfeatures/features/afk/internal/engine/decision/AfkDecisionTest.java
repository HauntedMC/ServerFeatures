package nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkDecisionTest {

    @Test
    void ofDeduplicatesActionsAndKeepsPriority() {
        AfkDecision decision = AfkDecision.of(
                AfkPriority.HIGH,
                AfkDecisionType.LEAVE_AFK,
                AfkDecisionType.LEAVE_AFK,
                AfkDecisionType.TOUCH_ACTIVITY
        );

        assertEquals(AfkPriority.HIGH, decision.priority());
        assertEquals(2, decision.actions().size());
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
        assertTrue(!decision.isNoop());
    }

    @Test
    void noneProducesLowPriorityNoopDecision() {
        AfkDecision none = AfkDecision.none();

        assertEquals(AfkPriority.LOW, none.priority());
        assertTrue(none.actions().isEmpty());
        assertTrue(none.isNoop());
    }

    @Test
    void ofWithoutActionsIsNoop() {
        AfkDecision decision = AfkDecision.of(AfkPriority.MEDIUM);

        assertTrue(decision.isNoop());
        assertTrue(decision.actions().isEmpty());
    }
}

