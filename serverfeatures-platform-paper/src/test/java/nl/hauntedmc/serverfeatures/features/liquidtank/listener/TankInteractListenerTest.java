package nl.hauntedmc.serverfeatures.features.liquidtank.listener;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.LiquidTankManager;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TankInteractListenerTest {

    @Test
    void unrelatedInteractionsDoNotLookupTanksOrScheduleWork() {
        LiquidTank feature = mock(LiquidTank.class);
        LiquidTankManager manager = mock(LiquidTankManager.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(feature.getTankManager()).thenReturn(manager);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_AIR);

        new TankInteractListener(feature).rightClickOnLiquidTank(event);

        verify(manager, never()).getTank(org.mockito.ArgumentMatchers.any(Block.class));
        verify(event, never()).setCancelled(true);
    }

    @Test
    void handlesTankInteractionSynchronously() {
        Fixture fixture = fixture();
        when(fixture.manager.isPermissionRequired()).thenReturn(false);

        new TankInteractListener(fixture.feature).rightClickOnLiquidTank(fixture.event);

        verify(fixture.event).setCancelled(true);
        verify(fixture.tank).onInteract(fixture.player);
        verify(fixture.feature, never()).getLifecycleManager();
    }

    @Test
    void permissionGateCancelsTankAccessWithoutInteraction() {
        Fixture fixture = fixture();
        when(fixture.manager.isPermissionRequired()).thenReturn(true);
        when(fixture.player.hasPermission("serverfeatures.feature.liquidtank.use"))
                .thenReturn(false);

        new TankInteractListener(fixture.feature).rightClickOnLiquidTank(fixture.event);

        verify(fixture.event).setCancelled(true);
        verify(fixture.tank, never()).onInteract(fixture.player);
    }

    @Test
    void sameTypeTransferEmptiesSourceWithoutRebuildingItTwice() {
        LiquidTankManager manager = mock(LiquidTankManager.class);
        AbstractTank source = mock(AbstractTank.class);
        AbstractTank destination = mock(AbstractTank.class);
        AbstractTank emptied = mock(AbstractTank.class);
        when(source.getTankType()).thenReturn(TankType.WATER);
        when(destination.getTankType()).thenReturn(TankType.WATER);
        when(source.getQuantity()).thenReturn(10);
        when(destination.getQuantity()).thenReturn(5);
        when(destination.getMaxQuantity()).thenReturn(128);
        when(manager.emptyTank(source)).thenReturn(emptied);

        TankInteractListener.transfer(manager, source, destination);

        verify(destination).setQuantity(15);
        verify(destination).updateVisuals();
        verify(manager).emptyTank(source);
        verify(emptied).setOnCooldown();
        verify(source, never()).updateVisuals();
    }

    @Test
    void overflowTransferFillsEmptyDestinationAndUpdatesRemainder() {
        LiquidTankManager manager = mock(LiquidTankManager.class);
        AbstractTank source = mock(AbstractTank.class);
        AbstractTank destination = mock(AbstractTank.class);
        AbstractTank replacement = mock(AbstractTank.class);
        when(source.getTankType()).thenReturn(TankType.EXPERIENCE);
        when(destination.getTankType()).thenReturn(TankType.EMPTY);
        when(source.getQuantity()).thenReturn(1_500);
        when(source.getMaxQuantity()).thenReturn(1_395);
        when(manager.changeTankType(destination, TankType.EXPERIENCE, 1_395))
                .thenReturn(replacement);

        TankInteractListener.transfer(manager, source, destination);

        verify(replacement).setOnCooldown();
        verify(source).setQuantity(105);
        verify(source).setOnCooldown();
        verify(source).updateVisuals();
    }

    @Test
    void zeroQuantitySourceDoesNotMutateEitherTank() {
        LiquidTankManager manager = mock(LiquidTankManager.class);
        AbstractTank source = mock(AbstractTank.class);
        AbstractTank destination = mock(AbstractTank.class);
        when(source.getTankType()).thenReturn(TankType.WATER);
        when(destination.getTankType()).thenReturn(TankType.WATER);
        when(source.getQuantity()).thenReturn(0);

        TankInteractListener.transfer(manager, source, destination);

        verify(source, never()).setQuantity(org.mockito.ArgumentMatchers.anyInt());
        verify(destination, never()).setQuantity(org.mockito.ArgumentMatchers.anyInt());
        verify(manager, never()).emptyTank(source);
    }

    private static Fixture fixture() {
        LiquidTank feature = mock(LiquidTank.class);
        LiquidTankManager manager = mock(LiquidTankManager.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        AbstractTank tank = mock(AbstractTank.class);
        when(feature.getTankManager()).thenReturn(manager);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(block.getType()).thenReturn(Material.HOPPER);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(manager.getTank(block)).thenReturn(tank);
        return new Fixture(feature, manager, event, player, tank);
    }

    private record Fixture(
            LiquidTank feature,
            LiquidTankManager manager,
            PlayerInteractEvent event,
            Player player,
            AbstractTank tank
    ) {
    }
}
