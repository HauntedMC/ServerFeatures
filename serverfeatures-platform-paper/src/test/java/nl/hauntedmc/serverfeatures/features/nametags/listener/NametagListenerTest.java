package nl.hauntedmc.serverfeatures.features.nametags.listener;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.NametagManager;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NametagListenerTest {

    @Test
    void joinWaitsForSelfViewLoadBeforeSchedulingInitialNametag() {
        Nametags feature = mock(Nametags.class);
        NametagManager manager = mock(NametagManager.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        Player player = mock(Player.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(feature.getNametagManager()).thenReturn(manager);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(lifecycle.getTaskManager()).thenReturn(tasks);

        NametagListener listener = new NametagListener(feature);
        try (MockedStatic<DataRegistryIdentityGate> identityGate = mockStatic(DataRegistryIdentityGate.class)) {
            identityGate.when(() -> DataRegistryIdentityGate.runWhenReady(
                            same(feature),
                            same(player),
                            org.mockito.ArgumentMatchers.<Consumer<Player>>any(),
                            eq("nametag self-view preload")
                    ))
                    .thenAnswer(invocation -> {
                        Consumer<Player> action = invocation.getArgument(2);
                        action.accept(player);
                        return null;
                    });

            listener.onPlayerJoin(event);
        }

        verify(tasks, never()).scheduleDelayedTask(any(Runnable.class), any(BukkitTime.class));

        ArgumentCaptor<Runnable> loadedCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(manager).preloadSelfView(same(player), loadedCallback.capture());
        loadedCallback.getValue().run();

        ArgumentCaptor<Runnable> initialUpdate = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<BukkitTime> delay = ArgumentCaptor.forClass(BukkitTime.class);
        verify(tasks).scheduleDelayedTask(initialUpdate.capture(), delay.capture());
        assertEquals(10L, delay.getValue().toTicks());

        verify(manager, never()).updateNametag(any(Player.class), any(UpdateProperties.class));
        initialUpdate.getValue().run();
        verify(manager).updateNametag(same(player), any(UpdateProperties.class));
    }
}
