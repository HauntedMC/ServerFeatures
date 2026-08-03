package nl.hauntedmc.serverfeatures.features.autopickup.transfer;

import nl.hauntedmc.serverfeatures.features.autopickup.model.DropScope;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectDropOriginClassifierTest {

    private final DirectDropOriginClassifier classifier = new DirectDropOriginClassifier();

    @Test
    void strictModeAcceptsOnlyTheDirectBlockCoordinate() {
        World world = world();
        BlockState state = state(world, 10, 64, 10, mock(BlockData.class));

        assertTrue(classifier.eligible(
                state,
                item(world, 10.75, 64.15, 10.25),
                DropScope.STRICT_DIRECT
        ));
        assertFalse(classifier.eligible(
                state,
                item(world, 11.05, 64.15, 10.25),
                DropScope.STRICT_DIRECT
        ));
    }

    @Test
    void eventAllAcceptsOffOriginDrops() {
        World world = world();
        BlockState state = state(world, 10, 64, 10, mock(BlockData.class));

        assertTrue(classifier.eligible(
                state,
                item(world, 100.0, 20.0, -50.0),
                DropScope.EVENT_ALL
        ));
    }

    @Test
    void strictModeAcceptsTheOtherHalfOfABisectedBlock() {
        World world = world();
        Bisected data = mock(Bisected.class);
        when(data.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        BlockState state = state(world, 10, 64, 10, data);

        assertTrue(classifier.eligible(
                state,
                item(world, 10.5, 65.2, 10.5),
                DropScope.STRICT_DIRECT
        ));
    }

    @Test
    void strictModeAcceptsTheOtherBedHalf() {
        World world = world();
        Bed data = mock(Bed.class);
        when(data.getPart()).thenReturn(Bed.Part.FOOT);
        when(data.getFacing()).thenReturn(BlockFace.EAST);
        BlockState state = state(world, 10, 64, 10, data);

        assertTrue(classifier.eligible(
                state,
                item(world, 11.5, 64.2, 10.5),
                DropScope.STRICT_DIRECT
        ));
        assertFalse(classifier.eligible(
                state,
                item(world, 9.5, 64.2, 10.5),
                DropScope.STRICT_DIRECT
        ));
    }

    private static World world() {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        return world;
    }

    private static BlockState state(World world, int x, int y, int z, BlockData data) {
        BlockState state = mock(BlockState.class);
        when(state.getLocation()).thenReturn(new Location(world, x, y, z));
        when(state.getBlockData()).thenReturn(data);
        return state;
    }

    private static Item item(World world, double x, double y, double z) {
        Item item = mock(Item.class);
        when(item.getLocation()).thenReturn(new Location(world, x, y, z));
        return item;
    }
}
