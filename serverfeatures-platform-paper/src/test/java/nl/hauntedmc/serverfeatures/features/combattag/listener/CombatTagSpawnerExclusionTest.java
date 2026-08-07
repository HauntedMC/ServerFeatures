package nl.hauntedmc.serverfeatures.features.combattag.listener;

import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.service.CombatTagService;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatTagSpawnerExclusionTest {

    @Test
    void attackingSpawnerEnemyDoesNotTagThePlayer() {
        CombatTagSettings settings = settings(Set.of(CreatureSpawnEvent.SpawnReason.SPAWNER));
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = new CombatTagListener(
                settings,
                service,
                new CombatSourceResolver(settings.attribution()),
                mock(FeatureTaskManager.class)
        );
        Player attacker = player();
        Enemy spawnerMob = hostileMob(CreatureSpawnEvent.SpawnReason.SPAWNER);

        listener.onDamage(damageEvent(attacker, spawnerMob));

        verify(service, never()).tagIncoming(any(), any(), any(), any());
        verify(service, never()).tagOutgoing(any(), any(), any(), any());
    }

    private static EntityDamageByEntityEvent damageEvent(Entity damager, LivingEntity target) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        DamageSource damageSource = mock(DamageSource.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(target);
        when(event.getFinalDamage()).thenReturn(1.0D);
        when(event.getDamageSource()).thenReturn(damageSource);
        when(damageSource.getDirectEntity()).thenReturn(damager);
        when(damageSource.getCausingEntity()).thenReturn(damager);
        return event;
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getName()).thenReturn("Attacker");
        return player;
    }

    private static Enemy hostileMob(CreatureSpawnEvent.SpawnReason spawnReason) {
        Enemy mob = mock(Enemy.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getType()).thenReturn(EntityType.ZOMBIE);
        when(mob.getName()).thenReturn("Zombie");
        when(mob.getEntitySpawnReason()).thenReturn(spawnReason);
        return mob;
    }

    private static CombatTagSettings settings(Set<CreatureSpawnEvent.SpawnReason> exclusions) {
        return new CombatTagSettings(
                new CombatTagSettings.TaggingSettings(
                        CombatTagSettings.TagMode.BOTH,
                        15,
                        false,
                        new CombatTagSettings.WorldRule(CombatTagSettings.WorldMode.ALL, Set.of())
                ),
                new CombatTagSettings.AttributionSettings(
                        true,
                        new CombatTagSettings.ProjectileSettings(true, Set.of()),
                        true,
                        true,
                        exclusions
                ),
                new CombatTagSettings.LifecycleSettings(true, true),
                new CombatTagSettings.TeleportSettings(true, true, Set.of(), false, false),
                new CombatTagSettings.LogoutSettings(false, false, false, false, List.of()),
                new CombatTagSettings.DisplaySettings(
                        false,
                        false,
                        new CombatTagSettings.ActionBarSettings(false, 5, 20, "█", "█")
                ),
                new CombatTagSettings.FeedbackSettings(0L)
        );
    }
}
