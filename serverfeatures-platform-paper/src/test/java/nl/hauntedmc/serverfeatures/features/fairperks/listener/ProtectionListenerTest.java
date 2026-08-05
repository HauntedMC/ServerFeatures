package nl.hauntedmc.serverfeatures.features.fairperks.listener;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.service.PerkStateService;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionListenerTest {

    @Test
    void landingGraceIsClearedOnNextTickInsteadOfDuringMoveEvent() {
        FairPerks feature = mock(FairPerks.class);
        PerkStateService stateService = mock(PerkStateService.class);
        FeatureLifecycleManager lifecycleManager = mock(FeatureLifecycleManager.class);
        FeatureTaskManager taskManager = mock(FeatureTaskManager.class);
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        Player player = mock(Player.class);

        when(feature.stateService()).thenReturn(stateService);
        when(feature.getLifecycleManager()).thenReturn(lifecycleManager);
        when(lifecycleManager.getTaskManager()).thenReturn(taskManager);
        when(event.getPlayer()).thenReturn(player);
        when(event.hasChangedBlock()).thenReturn(true);
        when(((Entity) player).isOnGround()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(stateService.view(player)).thenReturn(new PerkStateService.RuntimeView(
                false,
                false,
                false,
                false,
                false,
                false,
                true
        ));

        ProtectionListener listener = new ProtectionListener(feature);
        listener.onMove(event);

        verify(stateService, never()).clearFallDamageGrace(player);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManager).scheduleOneTimeTask(task.capture());

        task.getValue().run();

        verify(stateService).clearFallDamageGrace(player);
    }
}
