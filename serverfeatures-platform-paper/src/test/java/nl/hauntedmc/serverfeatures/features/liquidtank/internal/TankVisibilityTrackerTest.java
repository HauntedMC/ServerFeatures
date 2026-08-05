package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TankVisibilityTrackerTest {

    @Test
    void reportsOnlyVisibilityTransitions() {
        TankVisibilityTracker tracker = new TankVisibilityTracker();
        UUID playerId = UUID.randomUUID();
        AbstractTank first = mock(AbstractTank.class);
        AbstractTank second = mock(AbstractTank.class);

        TankVisibilityTracker.Delta initial = tracker.update(playerId, Set.of(first));
        TankVisibilityTracker.Delta unchanged = tracker.update(playerId, Set.of(first));
        TankVisibilityTracker.Delta moved = tracker.update(playerId, Set.of(second));

        assertEquals(Set.of(first), initial.added());
        assertTrue(initial.removed().isEmpty());
        assertTrue(unchanged.added().isEmpty());
        assertTrue(unchanged.removed().isEmpty());
        assertEquals(Set.of(second), moved.added());
        assertEquals(Set.of(first), moved.removed());
    }

    @Test
    void removingTankAndPlayerClearsTrackedVisibility() {
        TankVisibilityTracker tracker = new TankVisibilityTracker();
        UUID playerId = UUID.randomUUID();
        AbstractTank first = mock(AbstractTank.class);
        AbstractTank second = mock(AbstractTank.class);
        tracker.update(playerId, Set.of(first, second));

        tracker.removeTank(first);

        assertEquals(Set.of(second), tracker.removePlayer(playerId));
        assertTrue(tracker.removePlayer(playerId).isEmpty());
    }

    @Test
    void forgettingOneChunkPreservesOtherTrackedTanks() {
        TankVisibilityTracker tracker = new TankVisibilityTracker();
        UUID playerId = UUID.randomUUID();
        AbstractTank first = mock(AbstractTank.class);
        AbstractTank second = mock(AbstractTank.class);
        tracker.update(playerId, Set.of(first, second));

        tracker.forget(playerId, Set.of(first));

        assertEquals(Set.of(second), tracker.removePlayer(playerId));
    }
}
