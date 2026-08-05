package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.LiquidTankManager;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TankPlayerListenerTest {

    @Test
    void ignoresOrientationAndSubBlockMovement() {
        LiquidTank feature = mock(LiquidTank.class);
        LiquidTankManager manager = mock(LiquidTankManager.class);
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(feature.getTankManager()).thenReturn(manager);
        when(event.hasChangedBlock()).thenReturn(false);

        new TankPlayerListener(feature).onMove(event);

        verify(manager, never()).refreshPlayerView(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void refreshesOnlyMovingPlayerAfterBlockChange() {
        LiquidTank feature = mock(LiquidTank.class);
        LiquidTankManager manager = mock(LiquidTankManager.class);
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        Player player = mock(Player.class);
        Location destination = mock(Location.class);
        when(feature.getTankManager()).thenReturn(manager);
        when(event.hasChangedBlock()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(event.getTo()).thenReturn(destination);

        new TankPlayerListener(feature).onMove(event);

        verify(manager).refreshPlayerView(player, destination);
    }

    @Test
    void ignoresChunkPacketsThatContainNoTanks() {
        LiquidTank feature = mock(LiquidTank.class);
        LiquidTankManager manager = mock(LiquidTankManager.class);
        PlayerChunkLoadEvent event = mock(PlayerChunkLoadEvent.class);
        Chunk chunk = mock(Chunk.class);
        when(feature.getTankManager()).thenReturn(manager);
        when(event.getChunk()).thenReturn(chunk);
        when(manager.hasTankInChunk(chunk)).thenReturn(false);

        new TankPlayerListener(feature).onChunkLoad(event);

        verify(feature, never()).getLifecycleManager();
    }
}
