package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import nl.hauntedmc.serverfeatures.features.liquidtank.LiquidTank;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractTankVisibilityTest {

    @Test
    void usesSquaredDistanceAndWorldIdentity() {
        World world = mock(World.class);
        World otherWorld = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        TestTank tank = new TestTank(new Location(world, 0, 64, 0));

        assertTrue(tank.isVisibleFrom(new Location(world, 20, 64, 0)));
        assertFalse(tank.isVisibleFrom(new Location(world, 20.01, 64, 0)));
        assertFalse(tank.isVisibleFrom(new Location(otherWorld, 0, 64, 0)));
    }

    @Test
    void viewerMembershipIsSetBasedAndCanBeForgottenWithoutPackets() {
        World world = mock(World.class);
        TestTank tank = new TestTank(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        tank.showTo(player);
        tank.showTo(player);
        assertEqualsOne(tank.viewerIds().size());

        tank.forgetViewer(playerId);
        assertTrue(tank.viewerIds().isEmpty());
    }

    private static void assertEqualsOne(int value) {
        org.junit.jupiter.api.Assertions.assertEquals(1, value);
    }

    private static final class TestTank extends AbstractTank {
        private TestTank(Location location) {
            super(location, 0, mock(LiquidTank.class));
        }

        @Override
        protected String getLiquidHeadUrl() {
            return "";
        }

        @Override
        protected void showParticles() {
        }
    }
}
