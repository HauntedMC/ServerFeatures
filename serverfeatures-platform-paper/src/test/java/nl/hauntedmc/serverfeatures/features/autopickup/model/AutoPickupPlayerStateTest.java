package nl.hauntedmc.serverfeatures.features.autopickup.model;

import nl.hauntedmc.serverfeatures.features.autopickup.model.AutoPickupPlayerState.CommandIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPickupPlayerStateTest {

    @Test
    void twoQueuedTogglesCancelEachOther() {
        AutoPickupPlayerState state = new AutoPickupPlayerState();

        state.queuePendingCommand(CommandIntent.TOGGLE);
        state.queuePendingCommand(CommandIntent.TOGGLE);

        assertNull(state.pendingCommand());
    }

    @Test
    void queuedToggleComposesWithExplicitState() {
        AutoPickupPlayerState state = new AutoPickupPlayerState();

        state.queuePendingCommand(CommandIntent.ENABLE);
        state.queuePendingCommand(CommandIntent.TOGGLE);

        assertEquals(CommandIntent.DISABLE, state.pendingCommand());
    }

    @Test
    void latestExplicitStateSupersedesRelativeIntent() {
        AutoPickupPlayerState state = new AutoPickupPlayerState();

        state.queuePendingCommand(CommandIntent.TOGGLE);
        state.queuePendingCommand(CommandIntent.DISABLE);
        state.queuePendingCommand(CommandIntent.ENABLE);

        assertEquals(CommandIntent.ENABLE, state.pendingCommand());
    }

    @Test
    void statusCannotBeQueuedAsMutation() {
        AutoPickupPlayerState state = new AutoPickupPlayerState();

        assertThrows(
                IllegalArgumentException.class,
                () -> state.queuePendingCommand(CommandIntent.STATUS)
        );
    }
}
