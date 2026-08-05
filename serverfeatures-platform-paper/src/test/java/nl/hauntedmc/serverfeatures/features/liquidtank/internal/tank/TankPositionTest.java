package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TankPositionTest {

    @Test
    void derivesCorrectNegativeChunkCoordinates() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);

        TankPosition position = TankPosition.of(new Location(world, -1.0, 64.0, -17.0));

        assertEquals(new TankPosition(worldId, -1, 64, -17), position);
        assertEquals(new TankChunkKey(worldId, -1, -2), position.chunkKey());
    }
}
