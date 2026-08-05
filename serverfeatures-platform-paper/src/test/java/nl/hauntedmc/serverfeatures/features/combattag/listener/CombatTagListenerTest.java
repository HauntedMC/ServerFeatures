package nl.hauntedmc.serverfeatures.features.combattag.listener;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.service.CombatTagService;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatTagListenerTest {

    @Test
    void excludedMobSpawnReasonDoesNotTagTheDamagedPlayer() {
        CombatTagSettings settings = settings(Set.of(CreatureSpawnEvent.SpawnReason.SPAWNER));
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        LivingEntity mob = mob(CreatureSpawnEvent.SpawnReason.SPAWNER);
        Player target = player("Target");
        EntityDamageByEntityEvent event = damageEvent(mob, target);

        listener.onDamage(event);

        verify(service, never()).tagIncoming(any(), any(), any(), any());
        verify(service, never()).tagOutgoing(any(), any(), any(), any());
    }

    @Test
    void naturalMobDamageTagsTheDamagedPlayer() {
        CombatTagSettings settings = settings(Set.of(CreatureSpawnEvent.SpawnReason.SPAWNER));
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        LivingEntity mob = mob(CreatureSpawnEvent.SpawnReason.NATURAL);
        UUID mobId = mob.getUniqueId();
        Player target = player("Target");
        EntityDamageByEntityEvent event = damageEvent(mob, target);

        listener.onDamage(event);

        verify(service).tagIncoming(
                eq(target),
                any(),
                eq(mobId),
                eq(CombatTagReason.MELEE)
        );
    }

    @Test
    void pvpDamageTagsBothParticipantsInTheCorrectDirection() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        Player attacker = player("Attacker");
        UUID attackerId = attacker.getUniqueId();
        Player target = player("Target");
        UUID targetId = target.getUniqueId();
        EntityDamageByEntityEvent event = damageEvent(attacker, target);

        listener.onDamage(event);

        verify(service).tagIncoming(
                eq(target),
                any(),
                eq(attackerId),
                eq(CombatTagReason.MELEE)
        );
        verify(service).tagOutgoing(
                eq(attacker),
                any(),
                eq(targetId),
                eq(CombatTagReason.MELEE)
        );
    }

    private static CombatTagListener listener(
            CombatTagSettings settings,
            CombatTagService service
    ) {
        return new CombatTagListener(
                settings,
                service,
                new CombatSourceResolver(settings.attribution())
        );
    }

    private static EntityDamageByEntityEvent damageEvent(
            org.bukkit.entity.Entity damager,
            LivingEntity target
    ) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(target);
        return event;
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static LivingEntity mob(CreatureSpawnEvent.SpawnReason spawnReason) {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getType()).thenReturn(EntityType.ZOMBIE);
        when(mob.getName()).thenReturn("Zombie");
        when(mob.getEntitySpawnReason()).thenReturn(spawnReason);
        return mob;
    }

    private static CombatTagSettings settings(
            Set<CreatureSpawnEvent.SpawnReason> exclusions
    ) {
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
                new CombatTagSettings.LogoutSettings(false, false, false, List.of()),
                new CombatTagSettings.DisplaySettings(
                        false,
                        false,
                        new CombatTagSettings.ActionBarSettings(false, 5, 20, "█", "█")
                ),
                new CombatTagSettings.FeedbackSettings(0L)
        );
    }
}
