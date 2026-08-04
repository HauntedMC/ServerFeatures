package nl.hauntedmc.serverfeatures.features.spawnertoggle;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
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
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isChunkSent(chunk)).thenReturn(false);
        SpawnerVisualService service = new SpawnerVisualService(ignored -> true);

        service.refresh(viewer, spawner);

        verify(spawner, never()).copy();
        verify(viewer, never()).sendBlockUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
