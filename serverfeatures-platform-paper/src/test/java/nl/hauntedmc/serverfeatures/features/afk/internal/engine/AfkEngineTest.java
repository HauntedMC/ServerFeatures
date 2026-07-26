package nl.hauntedmc.serverfeatures.features.afk.internal.engine;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkPriority;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules.AfkRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkEngineTest {

    private static final AfkServiceFacade FACADE = new AfkServiceFacade() {
        @Override
        public double moveThreshold() {
            return 0.1D;
        }

        @Override
        public float rotateThreshold() {
            return 10.0F;
        }

        @Override
        public long comboWindowMs() {
            return 1_000L;
        }

        @Override
        public boolean antiEnabled() {
            return false;
        }

        @Override
        public long antiWindowMs() {
            return 10_000L;
        }

        @Override
        public int antiMinSamples() {
            return 2;
        }

        @Override
        public long antiMeanMinMs() {
            return 100L;
        }

        @Override
        public long antiMeanMaxMs() {
            return 5_000L;
        }

        @Override
        public long antiStddevMaxMs() {
            return 500L;
        }

        @Override
        public boolean isAfkCommand(String raw) {
            return false;
        }

        @Override
        public long antiLockMs() {
            return 1_000L;
        }

        @Override
        public double verticalEpsilon() {
            return 0.05D;
        }
    };

    @Test
    void highestPriorityWinsAndSamePriorityMergesActions() {
        AfkRule low = (event, state, cfg) -> AfkDecision.of(AfkPriority.LOW, AfkDecisionType.TOUCH_ACTIVITY);
        AfkRule highA = (event, state, cfg) -> AfkDecision.of(AfkPriority.HIGH, AfkDecisionType.LEAVE_AFK);
        AfkRule highB = (event, state, cfg) -> AfkDecision.of(AfkPriority.HIGH, AfkDecisionType.CLEAR_SUSPICIOUS);

        AfkEngine engine = new AfkEngine(FACADE, List.of(low, highA, highB));
        AfkDecision decision = engine.evaluate(AfkEvent.of(null, AfkEventType.CHAT, 0L, null, null), new AfkPlayerState());

        assertEquals(AfkPriority.HIGH, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertTrue(decision.actions().contains(AfkDecisionType.CLEAR_SUSPICIOUS));
        assertTrue(!decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void nullAndNoopDecisionsAreIgnored() {
        AfkRule nullRule = (event, state, cfg) -> null;
        AfkRule noopRule = (event, state, cfg) -> AfkDecision.none();
        AfkRule medium = (event, state, cfg) -> AfkDecision.of(AfkPriority.MEDIUM, AfkDecisionType.ENTER_AFK);

        AfkEngine engine = new AfkEngine(FACADE, List.of(nullRule, noopRule, medium));
        AfkDecision decision = engine.evaluate(AfkEvent.of(null, AfkEventType.CHAT, 0L, null, null), new AfkPlayerState());

        assertEquals(AfkPriority.MEDIUM, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.ENTER_AFK));
    }

    @Test
    void returnsNoneWhenNoRuleProducesAction() {
        AfkRule noopA = (event, state, cfg) -> AfkDecision.none();
        AfkRule noopB = (event, state, cfg) -> AfkDecision.none();

        AfkEngine engine = new AfkEngine(FACADE, List.of(noopA, noopB));
        AfkDecision decision = engine.evaluate(AfkEvent.of(null, AfkEventType.CHAT, 0L, null, null), new AfkPlayerState());

        assertTrue(decision.isNoop());
    }

    @Test
    void defaultRuleSetProducesStrongActionForChat() {
        AfkEngine engine = new AfkEngine(FACADE);

        AfkDecision decision = engine.evaluate(
                AfkEvent.of(null, AfkEventType.CHAT, 0L, null, null),
                new AfkPlayerState()
        );

        assertEquals(AfkPriority.HIGH, decision.priority());
        assertTrue(decision.actions().contains(AfkDecisionType.LEAVE_AFK));
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }
}

