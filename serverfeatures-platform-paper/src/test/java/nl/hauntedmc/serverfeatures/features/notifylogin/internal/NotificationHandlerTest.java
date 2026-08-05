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

    @Test
    void enteringVanishAnnouncesOnlyAfterAVisibleJoinState() {
        assertTrue(NotificationHandler.shouldBroadcastVanishTransition(
                true,
                false,
                NotificationHandler.VisibilityState.VISIBLE
        ));
        assertTrue(NotificationHandler.shouldBroadcastVanishTransition(true, false, null));

        assertFalse(NotificationHandler.shouldBroadcastVanishTransition(
                true,
                true,
                NotificationHandler.VisibilityState.VISIBLE
        ));
        assertFalse(NotificationHandler.shouldBroadcastVanishTransition(
                true,
                false,
                NotificationHandler.VisibilityState.PENDING
        ));
        assertFalse(NotificationHandler.shouldBroadcastVanishTransition(
                true,
                false,
                NotificationHandler.VisibilityState.UNKNOWN
        ));
        assertFalse(NotificationHandler.shouldBroadcastVanishTransition(
                true,
                false,
                NotificationHandler.VisibilityState.HIDDEN
        ));
    }

    @Test
    void leavingVanishAnnouncesAJoinButRejectsDuplicateVisibleTransitions() {
        assertTrue(NotificationHandler.shouldBroadcastVanishTransition(
                false,
                false,
                NotificationHandler.VisibilityState.HIDDEN
        ));
        assertTrue(NotificationHandler.shouldBroadcastVanishTransition(
                false,
                true,
                NotificationHandler.VisibilityState.PENDING
        ));
        assertTrue(NotificationHandler.shouldBroadcastVanishTransition(false, false, null));
        assertFalse(NotificationHandler.shouldBroadcastVanishTransition(
                false,
                false,
                NotificationHandler.VisibilityState.VISIBLE
        ));
    }
}
