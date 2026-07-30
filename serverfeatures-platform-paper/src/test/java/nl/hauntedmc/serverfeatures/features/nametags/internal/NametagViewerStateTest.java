package nl.hauntedmc.serverfeatures.features.nametags.internal;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NametagViewerStateTest {

    @Test
    void generationInvalidatesOlderDelayedWork() {
        NametagViewerState state = new NametagViewerState();

        long first = state.nextGeneration();
        long second = state.nextGeneration();

        assertFalse(state.isCurrent(first));
        assertTrue(state.isCurrent(second));
    }

    @Test
    void tracksPendingAndSpawnedStateIndependently() {
        NametagViewerState state = new NametagViewerState();
        BukkitTask firstTask = mock(BukkitTask.class);
        BukkitTask replacement = mock(BukkitTask.class);

        assertNull(state.replacePendingSpawn(firstTask));
        assertTrue(state.hasPendingSpawn());
        assertSame(firstTask, state.replacePendingSpawn(replacement));
        assertSame(replacement, state.clearPendingSpawn());
        assertFalse(state.hasPendingSpawn());

        state.markSpawned();
        assertTrue(state.isSpawned());
        state.markHidden();
        assertFalse(state.isSpawned());
    }
}
