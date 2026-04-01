package nl.hauntedmc.serverfeatures.features.afk.internal.engine.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkPlayerStateTest {

    @Test
    void defaultsAreNotAfkAndNotSuspicious() {
        AfkPlayerState state = new AfkPlayerState();

        assertFalse(state.isAfk());
        assertFalse(state.isSuspicious());
        assertEquals(0L, state.lastActivity());
        assertEquals(0L, state.afkSince());
        assertEquals(0L, state.lastMove());
        assertEquals(0L, state.lastAux());
        assertEquals(0L, state.afkLockUntil());
        assertTrue(state.antiTimes().isEmpty());
    }

    @Test
    void tracksActivityAfkAndSuspicionState() {
        AfkPlayerState state = new AfkPlayerState();

        state.touchActivity(123L);
        state.setAfk(true);
        state.setAfkSince(456L);
        state.setSuspicious(true);

        assertEquals(123L, state.lastActivity());
        assertTrue(state.isAfk());
        assertEquals(456L, state.afkSince());
        assertTrue(state.isSuspicious());
    }

    @Test
    void afkLockAndResetComboSignalsBehaveAsExpected() {
        AfkPlayerState state = new AfkPlayerState();

        state.setAfkLockUntil(10_000L);
        state.setLastMove(1_000L);
        state.setLastAux(2_000L);

        assertTrue(state.isAfkLocked(9_999L));
        assertFalse(state.isAfkLocked(10_000L));

        state.resetComboSignals();

        assertEquals(0L, state.lastMove());
        assertEquals(0L, state.lastAux());
    }

    @Test
    void antiTimesCanBeCleared() {
        AfkPlayerState state = new AfkPlayerState();
        state.antiTimes().add(100L);
        state.antiTimes().add(200L);

        state.clearAntiTimes();

        assertTrue(state.antiTimes().isEmpty());
    }
}

