package nl.hauntedmc.serverfeatures.features.fairperks.listener;

import nl.hauntedmc.serverfeatures.features.fairperks.FairPerks;
import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import nl.hauntedmc.serverfeatures.features.fairperks.service.PerkStateService;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;
import java.util.UUID;

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

    @Test
    void protectedOwnersCannotDamageOtherEntitiesThroughTamedPets() {
        FairPerks feature = mock(FairPerks.class);
        FairPerksSettings settings = mock(FairPerksSettings.class);
        PerkStateService stateService = mock(PerkStateService.class);
        Player owner = mock(Player.class);
        Tameable pet = mock(Tameable.class);
        Entity target = mock(Entity.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

        when(feature.settings()).thenReturn(settings);
        when(feature.stateService()).thenReturn(stateService);
        when(settings.restrictions()).thenReturn(restrictions(true));
        when(pet.getOwner()).thenReturn(owner);
        when(event.getDamager()).thenReturn((Entity) pet);
        when(event.getEntity()).thenReturn(target);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stateService.isRestricted(owner)).thenReturn(true);
        when(stateService.activeRestrictionMessageSuffix(owner)).thenReturn("god");

        new ProtectionListener(feature).onPerkDamage(event);

        verify(event).setCancelled(true);
        verify(feature).sendActionBar(owner, "fairperks.restriction.pet.god");
    }

    private static FairPerksSettings.RestrictionSettings restrictions(boolean petDamage) {
        return new FairPerksSettings.RestrictionSettings(
                true,
                petDamage,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                5,
                5,
                10,
                Set.of()
        );
    }
}
