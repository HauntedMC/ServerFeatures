package nl.hauntedmc.serverfeatures.features.combattag.listener;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagResult;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import nl.hauntedmc.serverfeatures.features.combattag.event.CombatTagAppliedEvent;
import nl.hauntedmc.serverfeatures.features.combattag.service.CombatTagService;
import nl.hauntedmc.serverfeatures.features.combattag.source.CombatSourceResolver;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.Server;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
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
        LivingEntity mob = hostileMob(CreatureSpawnEvent.SpawnReason.SPAWNER);
        Player target = player("Target");
        EntityDamageByEntityEvent event = damageEvent(mob, target);

        listener.onDamage(event);

        verify(service, never()).tagIncoming(any(), any(), any(), any());
        verify(service, never()).tagOutgoing(any(), any(), any(), any());
    }

    @Test
    void naturalEnemyDamageTagsTheDamagedPlayer() {
        CombatTagSettings settings = settings(Set.of(CreatureSpawnEvent.SpawnReason.SPAWNER));
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        LivingEntity mob = hostileMob(CreatureSpawnEvent.SpawnReason.NATURAL);
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
    void passiveMobDamageDoesNotTagTheDamagedPlayer() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        Pig pig = passivePig();
        Player target = player("Target");

        listener.onDamage(damageEvent(pig, target));

        verify(service, never()).tagIncoming(any(), any(), any(), any());
        verify(service, never()).tagOutgoing(any(), any(), any(), any());
    }

    @Test
    void neutralMobActivelyTargetingThePlayerDoesTagOnDamage() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        Mob wolf = mock(Mob.class);
        UUID wolfId = UUID.randomUUID();
        Player target = player("Target");
        when(wolf.getUniqueId()).thenReturn(wolfId);
        when(wolf.getType()).thenReturn(EntityType.WOLF);
        when(wolf.getName()).thenReturn("Wolf");
        when(wolf.getEntitySpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(wolf.getTarget()).thenReturn(target);

        listener.onDamage(damageEvent(wolf, target));

        verify(service).tagIncoming(
                eq(target),
                any(),
                eq(wolfId),
                eq(CombatTagReason.MELEE)
        );
    }

    @Test
    void attackingPassiveMobDoesNotTagThePlayer() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        Player attacker = player("Attacker");
        Pig pig = passivePig();

        listener.onDamage(damageEvent(attacker, pig));

        verify(service, never()).tagIncoming(any(), any(), any(), any());
        verify(service, never()).tagOutgoing(any(), any(), any(), any());
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

    @Test
    void successfulTagPublishesAppliedEvent() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        LivingEntity mob = hostileMob(CreatureSpawnEvent.SpawnReason.NATURAL);
        Player target = player("Target");
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(target.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(service.tagIncoming(any(), any(), any(), any()))
                .thenReturn(CombatTagResult.TAGGED);

        listener.onDamage(damageEvent(mob, target));

        verify(pluginManager).callEvent(any(CombatTagAppliedEvent.class));
    }

    @Test
    void zeroFinalDamageDoesNotCreateOrRefreshCombat() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        Player attacker = player("Attacker");
        Player target = player("Target");
        EntityDamageByEntityEvent event = damageEvent(attacker, target);
        when(event.getFinalDamage()).thenReturn(0.0D);

        listener.onDamage(event);

        verify(service, never()).tagIncoming(any(), any(), any(), any());
        verify(service, never()).tagOutgoing(any(), any(), any(), any());
    }

    @Test
    void explodingCreeperIsClearedAndCannotImmediatelyRetagItsVictim() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        FeatureTaskManager taskManager = mock(FeatureTaskManager.class);
        CombatTagListener listener = listener(settings, service, taskManager);
        Creeper creeper = mock(Creeper.class);
        UUID creeperId = UUID.randomUUID();
        when(creeper.getUniqueId()).thenReturn(creeperId);
        when(creeper.getType()).thenReturn(EntityType.CREEPER);
        when(creeper.getName()).thenReturn("Creeper");
        when(creeper.getEntitySpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        ExplosionPrimeEvent prime = mock(ExplosionPrimeEvent.class);
        when(prime.getEntity()).thenReturn(creeper);
        Player target = player("Target");

        listener.onExplosionPrime(prime);
        listener.onDamage(damageEvent(creeper, target));

        verify(service).handleOpponentDeath(creeper);
        verify(service, never()).tagIncoming(any(), any(), any(), any());
    }

    @Test
    void kickStateIsForwardedToTheQuitPolicy() {
        CombatTagSettings settings = settings(Set.of());
        CombatTagService service = mock(CombatTagService.class);
        CombatTagListener listener = listener(settings, service);
        Player player = player("Kicked");
        PlayerKickEvent kick = mock(PlayerKickEvent.class);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(kick.getPlayer()).thenReturn(player);
        when(quit.getPlayer()).thenReturn(player);

        listener.onKick(kick);
        listener.onQuit(quit);

        verify(service).handleQuit(player, true);
    }

    private static CombatTagListener listener(
            CombatTagSettings settings,
            CombatTagService service
    ) {
        return listener(settings, service, mock(FeatureTaskManager.class));
    }

    private static CombatTagListener listener(
            CombatTagSettings settings,
            CombatTagService service,
            FeatureTaskManager taskManager
    ) {
        return new CombatTagListener(
                settings,
                service,
                new CombatSourceResolver(settings.attribution()),
                taskManager
        );
    }

    private static EntityDamageByEntityEvent damageEvent(
            org.bukkit.entity.Entity damager,
            LivingEntity target
    ) {
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

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static LivingEntity hostileMob(CreatureSpawnEvent.SpawnReason spawnReason) {
        Enemy mob = mock(Enemy.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getType()).thenReturn(EntityType.ZOMBIE);
        when(mob.getName()).thenReturn("Zombie");
        when(mob.getEntitySpawnReason()).thenReturn(spawnReason);
        return mob;
    }

    private static Pig passivePig() {
        Pig pig = mock(Pig.class);
        when(pig.getUniqueId()).thenReturn(UUID.randomUUID());
        when(pig.getType()).thenReturn(EntityType.PIG);
        when(pig.getName()).thenReturn("Pig");
        when(pig.getEntitySpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        return pig;
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
