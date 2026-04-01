package nl.hauntedmc.serverfeatures.features.afk.internal.engine.rules;

import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecision;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.decision.AfkDecisionType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEvent;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.event.AfkEventType;
import nl.hauntedmc.serverfeatures.features.afk.internal.engine.player.AfkPlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportRuleTest {

    private final TeleportRule rule = new TeleportRule();

    @Test
    void teleportTouchesActivityForCleanNonAfkState() {
        AfkPlayerState state = new AfkPlayerState();
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.TELEPORT, 5_000L, null, null),
                state,
                new RuleTestFacade()
        );

        assertEquals(5_000L, state.lastMove());
        assertTrue(decision.actions().contains(AfkDecisionType.TOUCH_ACTIVITY));
    }

    @Test
    void teleportDoesNotTouchWhenAfkOrSuspicious() {
        AfkPlayerState afk = new AfkPlayerState();
        afk.setAfk(true);

        AfkDecision afkDecision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.TELEPORT, 1_000L, null, null),
                afk,
                new RuleTestFacade()
        );
        assertTrue(afkDecision.isNoop());

        AfkPlayerState suspicious = new AfkPlayerState();
        suspicious.setSuspicious(true);

        AfkDecision suspiciousDecision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.TELEPORT, 2_000L, null, null),
                suspicious,
                new RuleTestFacade()
        );
        assertTrue(suspiciousDecision.isNoop());
    }

    @Test
    void nonTeleportEventReturnsNoop() {
        AfkDecision decision = rule.evaluate(
                AfkEvent.of(null, AfkEventType.MOVE, 0L, null, null),
                new AfkPlayerState(),
                new RuleTestFacade()
        );

        assertTrue(decision.isNoop());
    }
}

