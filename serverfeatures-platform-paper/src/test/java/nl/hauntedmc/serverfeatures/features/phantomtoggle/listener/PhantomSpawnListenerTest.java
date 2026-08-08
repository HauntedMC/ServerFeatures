package nl.hauntedmc.serverfeatures.features.phantomtoggle.listener;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.PhantomToggle;
import nl.hauntedmc.serverfeatures.features.phantomtoggle.persistence.PhantomPreferenceService;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhantomSpawnListenerTest {

    @Test
    void suppressesPhantomSpawnForOptedOutPlayer() {
        PhantomToggle feature = mock(PhantomToggle.class);
        PhantomPreferenceService preferences = mock(PhantomPreferenceService.class);
        PhantomPreSpawnEvent event = mock(PhantomPreSpawnEvent.class);
        Player player = mock(Player.class);
        when(feature.preferences()).thenReturn(preferences);
        when(event.getSpawningEntity()).thenReturn(player);
        when(preferences.shouldSuppressSpawn(player)).thenReturn(true);

        new PhantomSpawnListener(feature).onPhantomPreSpawn(event);

        verify(event).setCancelled(true);
        verify(event).setShouldAbortSpawn(true);
    }

    @Test
    void leavesAllowedPlayerSpawnUntouched() {
        PhantomToggle feature = mock(PhantomToggle.class);
        PhantomPreferenceService preferences = mock(PhantomPreferenceService.class);
        PhantomPreSpawnEvent event = mock(PhantomPreSpawnEvent.class);
        Player player = mock(Player.class);
        when(feature.preferences()).thenReturn(preferences);
        when(event.getSpawningEntity()).thenReturn(player);
        when(preferences.shouldSuppressSpawn(player)).thenReturn(false);

        new PhantomSpawnListener(feature).onPhantomPreSpawn(event);

        verify(event, never()).setCancelled(true);
        verify(event, never()).setShouldAbortSpawn(true);
    }

    @Test
    void ignoresNonPlayerSpawningEntity() {
        PhantomToggle feature = mock(PhantomToggle.class);
        PhantomPreferenceService preferences = mock(PhantomPreferenceService.class);
        PhantomPreSpawnEvent event = mock(PhantomPreSpawnEvent.class);
        Entity entity = mock(Entity.class);
        when(feature.preferences()).thenReturn(preferences);
        when(event.getSpawningEntity()).thenReturn(entity);

        new PhantomSpawnListener(feature).onPhantomPreSpawn(event);

        verify(preferences, never()).shouldSuppressSpawn(any());
        verify(event, never()).setCancelled(true);
        verify(event, never()).setShouldAbortSpawn(true);
    }

    @Test
    void creatureSpawnFallbackSuppressesNaturalPhantomForOptedOutPlayer() {
        PhantomToggle feature = mock(PhantomToggle.class);
        PhantomPreferenceService preferences = mock(PhantomPreferenceService.class);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        Phantom phantom = mock(Phantom.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(feature.preferences()).thenReturn(preferences);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(event.getEntity()).thenReturn(phantom);
        when(phantom.getSpawningEntity()).thenReturn(playerId);
        when(phantom.getWorld()).thenReturn(world);
        when(world.getEntity(playerId)).thenReturn(player);
        when(preferences.shouldSuppressSpawn(player)).thenReturn(true);

        new PhantomSpawnListener(feature).onCreatureSpawn(event);

        verify(event).setCancelled(true);
    }

    @Test
    void creatureSpawnFallbackIgnoresCustomPhantomSpawn() {
        PhantomToggle feature = mock(PhantomToggle.class);
        PhantomPreferenceService preferences = mock(PhantomPreferenceService.class);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        Phantom phantom = mock(Phantom.class);
        when(feature.preferences()).thenReturn(preferences);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.CUSTOM);
        when(event.getEntity()).thenReturn(phantom);

        new PhantomSpawnListener(feature).onCreatureSpawn(event);

        verify(preferences, never()).shouldSuppressSpawn(any());
        verify(event, never()).setCancelled(true);
    }

    @Test
    void creatureSpawnFallbackIgnoresPhantomWithoutSpawningEntity() {
        PhantomToggle feature = mock(PhantomToggle.class);
        PhantomPreferenceService preferences = mock(PhantomPreferenceService.class);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        Phantom phantom = mock(Phantom.class);
        when(feature.preferences()).thenReturn(preferences);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(event.getEntity()).thenReturn(phantom);
        when(phantom.getSpawningEntity()).thenReturn(null);

        new PhantomSpawnListener(feature).onCreatureSpawn(event);

        verify(preferences, never()).shouldSuppressSpawn(any());
        verify(event, never()).setCancelled(true);
    }
}
