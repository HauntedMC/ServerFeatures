package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankType;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiquidTankManagerTest {

    @Test
    void staleTankReferenceCannotReplaceIndexedState() {
        LiquidTankManager manager = new LiquidTankManager(mock(LiquidTank.class));
        AbstractTank staleTank = mock(AbstractTank.class);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(staleTank.getLocation()).thenReturn(new Location(world, 1, 2, 3));

        assertThrows(
                IllegalStateException.class,
                () -> manager.changeTankType(staleTank, TankType.WATER, 1)
        );
    }
}
