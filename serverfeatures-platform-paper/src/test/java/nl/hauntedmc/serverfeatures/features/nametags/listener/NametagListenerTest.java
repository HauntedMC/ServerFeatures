package nl.hauntedmc.serverfeatures.features.nametags.listener;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NametagListenerTest {

    @Test
    void joinWaitsForDataRegistryBeforeStartingManagerLifecycle() {
        Nametags feature = mock(Nametags.class);
        NametagManager manager = mock(NametagManager.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        Player player = mock(Player.class);
        when(event.getPlayer()).thenReturn(player);
        when(feature.getNametagManager()).thenReturn(manager);

        NametagListener listener = new NametagListener(feature);
        try (MockedStatic<DataRegistryIdentityGate> identityGate = mockStatic(DataRegistryIdentityGate.class)) {
            identityGate.when(() -> DataRegistryIdentityGate.runWhenReady(
                            same(feature),
                            same(player),
                            org.mockito.ArgumentMatchers.<Consumer<Player>>any(),
                            eq("nametag player initialization")
                    ))
                    .thenAnswer(invocation -> {
                        Consumer<Player> action = invocation.getArgument(2);
                        action.accept(player);
                        return null;
                    });

            listener.onPlayerJoin(event);
        }

        verify(manager).handleJoin(player);
    }

    @Test
    void trackingEventsDrivePairLifecycle() {
        Nametags feature = mock(Nametags.class);
        NametagManager manager = mock(NametagManager.class);
        Player viewer = mock(Player.class);
        Player owner = mock(Player.class);
        PlayerTrackEntityEvent track = mock(PlayerTrackEntityEvent.class);
        PlayerUntrackEntityEvent untrack = mock(PlayerUntrackEntityEvent.class);
        when(feature.getNametagManager()).thenReturn(manager);
        when(track.getPlayer()).thenReturn(viewer);
        when(track.getEntity()).thenReturn(owner);
        when(untrack.getPlayer()).thenReturn(viewer);
        when(untrack.getEntity()).thenReturn(owner);

        NametagListener listener = new NametagListener(feature);
        listener.onPlayerTracksEntity(track);
        listener.onPlayerUntracksEntity(untrack);

        verify(manager).onViewerTracks(viewer, owner);
        verify(manager).onViewerUntracks(viewer, owner);
    }

    @Test
    void longSameWorldTeleportStartsAFullViewerAndOwnerTransition() {
        Nametags feature = mock(Nametags.class);
        NametagManager manager = mock(NametagManager.class);
        Player player = mock(Player.class);
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        World world = mock(World.class);
        Location from = new Location(world, 0.0, 64.0, 0.0);
        Location to = new Location(world, 100.0, 64.0, 0.0);
        when(feature.getNametagManager()).thenReturn(manager);
        when(event.getPlayer()).thenReturn(player);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(manager.requiresTeleportRebuild(from, to)).thenReturn(true);

        new NametagListener(feature).onPlayerTeleport(event);

        verify(manager).beginPlayerTransition(player);
    }

    @Test
    void crossWorldTeleportDefersToChangedWorldLifecycle() {
        Nametags feature = mock(Nametags.class);
        NametagManager manager = mock(NametagManager.class);
        Player player = mock(Player.class);
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        World fromWorld = mock(World.class);
        World toWorld = mock(World.class);
        Location from = new Location(fromWorld, 0.0, 64.0, 0.0);
        Location to = new Location(toWorld, 0.0, 64.0, 0.0);
        when(feature.getNametagManager()).thenReturn(manager);
        when(event.getPlayer()).thenReturn(player);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        new NametagListener(feature).onPlayerTeleport(event);

        verify(manager, never()).beginPlayerTransition(player);
    }
}
