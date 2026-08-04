package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnerVisualServiceTest {

    @Test
    void disabledVisualUsesClientOnlyRangeZeroCopy() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        CreatureSpawner visualState = mock(CreatureSpawner.class);
        Player viewer = mock(Player.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Location location = mock(Location.class);
        when(spawner.getWorld()).thenReturn(world);
        when(spawner.getChunk()).thenReturn(chunk);
        when(spawner.getLocation()).thenReturn(location);
        when(spawner.copy()).thenReturn(visualState);
        when(chunk.isLoaded()).thenReturn(true);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isChunkSent(chunk)).thenReturn(true);
        SpawnerVisualService service = new SpawnerVisualService(ignored -> true);

        service.refresh(viewer, spawner);

        verify(visualState).setRequiredPlayerRange(
                SpawnerVisualService.DISABLED_VISUAL_REQUIRED_PLAYER_RANGE
        );
        verify(spawner, never()).setRequiredPlayerRange(anyInt());
        verify(viewer).sendBlockUpdate(location, visualState);
    }

    @Test
    void enabledVisualRestoresActualSpawnerState() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        Player viewer = mock(Player.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Location location = mock(Location.class);
        when(spawner.getWorld()).thenReturn(world);
        when(spawner.getChunk()).thenReturn(chunk);
        when(spawner.getLocation()).thenReturn(location);
        when(chunk.isLoaded()).thenReturn(true);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isChunkSent(chunk)).thenReturn(true);
        SpawnerVisualService service = new SpawnerVisualService(ignored -> false);

        service.refresh(viewer, spawner);

        verify(spawner, never()).copy();
        verify(viewer).sendBlockUpdate(location, spawner);
    }

    @Test
    void doesNotSendUpdatesForChunksTheViewerDoesNotHave() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        Player viewer = mock(Player.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(spawner.getWorld()).thenReturn(world);
        when(spawner.getChunk()).thenReturn(chunk);
        when(chunk.isLoaded()).thenReturn(true);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isChunkSent(chunk)).thenReturn(false);
        SpawnerVisualService service = new SpawnerVisualService(ignored -> true);

        service.refresh(viewer, spawner);

        verify(spawner, never()).copy();
        verify(viewer, never()).sendBlockUpdate(any(Location.class), any(TileState.class));
    }

    @Test
    void doesNotInspectUnloadedChunks() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        Player viewer = mock(Player.class);
        Chunk chunk = mock(Chunk.class);
        when(viewer.isOnline()).thenReturn(true);
        when(chunk.isLoaded()).thenReturn(false);
        SpawnerVisualService service = new SpawnerVisualService(ignored -> true);

        service.refreshChunk(viewer, chunk);

        verify(chunk, never()).getTileEntities();
        verify(chunk, never()).getTileEntities(any(), anyBoolean());
    }

    @Test
    void chunkRefreshFiltersSpawnerBlocksAndAvoidsSnapshots() {
        CreatureSpawner spawner = mock(CreatureSpawner.class);
        CreatureSpawner visualState = mock(CreatureSpawner.class);
        Player viewer = mock(Player.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Location location = mock(Location.class);
        Block spawnerBlock = mock(Block.class);
        Block chestBlock = mock(Block.class);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isChunkSent(chunk)).thenReturn(true);
        when(chunk.isLoaded()).thenReturn(true);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(spawner));
        when(spawner.getWorld()).thenReturn(world);
        when(spawner.getChunk()).thenReturn(chunk);
        when(spawner.getLocation()).thenReturn(location);
        when(spawner.copy()).thenReturn(visualState);
        when(spawnerBlock.getType()).thenReturn(Material.SPAWNER);
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        SpawnerVisualService service = new SpawnerVisualService(ignored -> true);

        service.refreshChunk(viewer, chunk);

        verify(chunk).getTileEntities(argThat(predicate ->
                predicate.test(spawnerBlock) && !predicate.test(chestBlock)
        ), eq(false));
        verify(viewer).sendBlockUpdate(location, visualState);
    }
}
