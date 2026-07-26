package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.util.Movement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementRuleTest {

    private final MovementRule rule = new MovementRule();

    @Test
    void unrelatedEventReturnsNoop() {
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.CHAT, 0L, null, null),
                new AfkPlayerState(),
                new RuleTestFacade()
        );

        assertTrue(decision.isNoop());
    }

    @Test
    void nonAfkHorizontalMoveTouchesActivity() {
        AfkPlayerState state = new AfkPlayerState();
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.moveThreshold = 0.10D;

        Movement move = movement(0, 64, 0, 0f, 0f, 1, 64, 0, 0f, 0f);
        AfkDecision decision = rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 1_000L, null, move), state, cfg);

        assertEquals(1_000L, state.lastMove());
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void rotationAcrossZeroUsesShortestAngle() {
        AfkPlayerState state = new AfkPlayerState();
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.rotateThreshold = 15.0F;

        Movement move = movement(0, 64, 0, 350f, 0f, 0, 64, 0, 10f, 0f);
        AfkDecision decision = rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 2_000L, null, move), state, cfg);

        assertEquals(2_000L, state.lastMove());
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void suspiciousStatePreventsTouchWhenNotAfk() {
        AfkPlayerState state = new AfkPlayerState();
        state.setSuspicious(true);

        Movement move = movement(0, 64, 0, 0f, 0f, 2, 64, 0, 0f, 0f);
        AfkDecision decision = rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 3_000L, null, move), state, new RuleTestFacade());

        assertTrue(decision.isNoop());
    }

    @Test
    void verticalOnlyPatternCanFlagSuspicious() {
        AfkPlayerState state = new AfkPlayerState();
        RuleTestFacade cfg = new RuleTestFacade();
        cfg.antiEnabled = true;
        cfg.antiMinSamples = 2;
        cfg.antiMeanMinMs = 900;
        cfg.antiMeanMaxMs = 1_100;
        cfg.antiStddevMaxMs = 10;
        cfg.verticalEpsilon = 0.05D;

        Movement verticalOnly = movement(0, 64, 0, 0f, 0f, 0, 65, 0, 0f, 0f);
        rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 1_000L, null, verticalOnly), state, cfg);
        rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 2_000L, null, verticalOnly), state, cfg);
        AfkDecision third = rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 3_000L, null, verticalOnly), state, cfg);

        assertEquals(AfkPriority.HIGH, third.priority());
        assertTrue(third.actions().contains(AfkDecisionType.FLAG_SUSPICIOUS));
    }

    @Test
    void afkComboLeavesAfkWhenRecentAuxAndUnlocked() {
        AfkPlayerState state = new AfkPlayerState();
        state.setAfk(true);
        state.setLastAux(1_000L);

        RuleTestFacade cfg = new RuleTestFacade();
        cfg.comboWindowMs = 2_000L;

        Movement move = movement(0, 64, 0, 0f, 0f, 1, 64, 0, 0f, 0f);
        AfkDecision decision = rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 2_000L, null, move), state, cfg);

        assertEquals(AfkPriority.MEDIUM, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
    }

    @Test
    void afkLockBlocksLeaveDecision() {
        AfkPlayerState state = new AfkPlayerState();
        state.setAfk(true);
        state.setLastAux(1_000L);
        state.setAfkLockUntil(10_000L);

        RuleTestFacade cfg = new RuleTestFacade();
        cfg.comboWindowMs = 2_000L;

        Movement move = movement(0, 64, 0, 0f, 0f, 1, 64, 0, 0f, 0f);
        AfkDecision decision = rule.evaluate(AfkEvent.of(null, AfkEventType.MOVE, 2_000L, null, move), state, cfg);

        assertTrue(decision.isNoop());
    }

    private static Movement movement(
            double fx, double fy, double fz, float fyaw, float fpitch,
            double tx, double ty, double tz, float tyaw, float tpitch
    ) {
        return new Movement(fx, fy, fz, fyaw, fpitch, tx, ty, tz, tyaw, tpitch);
    }
}

