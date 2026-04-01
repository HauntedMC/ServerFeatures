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

class WeakActionRuleTest {

    private final WeakActionRule rule = new WeakActionRule();

    @Test
    void unrelatedEventReturnsNoop() {
        AfkPlayerState state = new AfkPlayerState();

        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.CHAT, 100L, null, null),
                state,
                new RuleTestFacade()
        );

        assertTrue(decision.isNoop());
        assertEquals(0L, state.lastAux());
    }

    @Test
    void weakActionUpdatesLastAuxEvenWhenNotAfk() {
        AfkPlayerState state = new AfkPlayerState();

        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.WEAK_ACTION, 500L, null, null),
                state,
                new RuleTestFacade()
        );

        assertEquals(500L, state.lastAux());
        assertTrue(decision.isNoop());
    }

    @Test
    void afkComboWithRecentMoveLeavesAfk() {
        AfkPlayerState state = new AfkPlayerState();
        state.setAfk(true);
        state.setLastMove(1_000L);

        RuleTestFacade cfg = new RuleTestFacade();
        cfg.comboWindowMs = 2_000L;

        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.JUMP, 2_000L, null, null),
                state,
                cfg
        );

        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertEquals(AfkPriority.MEDIUM, decision.priority());
    }

    @Test
    void antiPatternFlagsSuspiciousWhenIntervalsAreRegular() {
        AfkPlayerState state = new AfkPlayerState();
        state.setAfk(true);

        RuleTestFacade cfg = new RuleTestFacade();
        cfg.antiEnabled = true;
        cfg.antiMinSamples = 2;
        cfg.antiMeanMinMs = 900;
        cfg.antiMeanMaxMs = 1_100;
        cfg.antiStddevMaxMs = 10;

        rule.evaluate(AfkEvent.of(null, AfkEventType.WEAK_ACTION, 1_000L, null, null), state, cfg);
        rule.evaluate(AfkEvent.of(null, AfkEventType.WEAK_ACTION, 2_000L, null, null), state, cfg);
        AfkDecision third = rule.evaluate(AfkEvent.of(null, AfkEventType.WEAK_ACTION, 3_000L, null, null), state, cfg);

        assertEquals(AfkPriority.HIGH, third.priority());
        assertTrue(third.actions().contains(AfkDecisionType.FLAG_SUSPICIOUS));
    }
}

