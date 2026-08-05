package nl.hauntedmc.serverfeatures.features.liquidtank.internal;

import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankChunkKey;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.TankPosition;
import nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl.AbstractTank;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TankIndexTest {

    @Test
    void indexesByExactPositionWorldAndChunk() {
        TankIndex index = new TankIndex();
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        AbstractTank first = mock(AbstractTank.class);
        AbstractTank second = mock(AbstractTank.class);
        TankPosition firstPosition = new TankPosition(firstWorld, -1, 70, -1);
        TankPosition secondPosition = new TankPosition(secondWorld, -1, 70, -1);

        index.put(firstPosition, first);
        index.put(secondPosition, second);

        assertSame(first, index.get(firstPosition));
        assertSame(second, index.get(secondPosition));
        assertEquals(1, index.count(new TankChunkKey(firstWorld, -1, -1)));
        assertEquals(java.util.Set.of(first), index.tanks(new TankChunkKey(firstWorld, -1, -1)));
        assertTrue(index.nearby(firstWorld, 0, 0, 1).contains(first));
        assertTrue(index.nearby(firstWorld, 0, 0, 1).stream().noneMatch(second::equals));
    }

    @Test
    void replacingPositionRemovesPreviousTankFromChunkIndex() {
        TankIndex index = new TankIndex();
        UUID worldId = UUID.randomUUID();
        TankPosition position = new TankPosition(worldId, 17, 64, 17);
        AbstractTank previous = mock(AbstractTank.class);
        AbstractTank replacement = mock(AbstractTank.class);

        index.put(position, previous);
        assertSame(previous, index.put(position, replacement));

        assertEquals(1, index.count(position.chunkKey()));
        assertEquals(java.util.List.of(replacement), index.nearby(worldId, 1, 1, 0));
    }
}
