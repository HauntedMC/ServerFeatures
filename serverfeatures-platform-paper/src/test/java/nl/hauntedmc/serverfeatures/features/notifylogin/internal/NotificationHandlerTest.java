package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationHandlerTest {

    @Test
    void quitVisibilityDecisionFailsClosedForUnresolvedAndHiddenStates() {
        assertTrue(NotificationHandler.shouldSuppressQuit(
                true,
                NotificationHandler.VisibilityState.VISIBLE,
                false
        ));
        assertTrue(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.PENDING,
                false
        ));
        assertTrue(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.UNKNOWN,
                false
        ));
        assertTrue(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.VISIBLE,
                true
        ));
        assertTrue(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.HIDDEN,
                null
        ));
    }

    @Test
    void quitVisibilityDecisionAllowsKnownVisibleAndExplicitlyUnvanishedPlayers() {
        assertFalse(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.VISIBLE,
                false
        ));
        assertFalse(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.VISIBLE,
                null
        ));
        assertFalse(NotificationHandler.shouldSuppressQuit(
                false,
                NotificationHandler.VisibilityState.HIDDEN,
                false
        ));
        assertFalse(NotificationHandler.shouldSuppressQuit(false, null, null));
    }
}
