package nl.hauntedmc.serverfeatures.features.bettercoral.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockFadeEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BetterCoralListenerTest {

    private final BetterCoralListener listener = new BetterCoralListener();

    @Test
    void cancelsTheMatchingCoralDryingTransition() {
        BlockFadeEvent event = event(Material.BUBBLE_CORAL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);

        listener.onCoralFade(event);

        verify(event).setCancelled(true);
    }

    @Test
    void leavesOtherFadeTransitionsUntouched() {
        BlockFadeEvent event = event(Material.BUBBLE_CORAL_FAN, Material.AIR);

        listener.onCoralFade(event);

        verify(event, never()).setCancelled(true);
    }

    private static BlockFadeEvent event(Material current, Material next) {
        Block block = mock(Block.class);
        BlockState newState = mock(BlockState.class);
        BlockFadeEvent event = mock(BlockFadeEvent.class);

        when(block.getType()).thenReturn(current);
        when(newState.getType()).thenReturn(next);
        when(event.getBlock()).thenReturn(block);
        when(event.getNewState()).thenReturn(newState);
        return event;
    }
}
