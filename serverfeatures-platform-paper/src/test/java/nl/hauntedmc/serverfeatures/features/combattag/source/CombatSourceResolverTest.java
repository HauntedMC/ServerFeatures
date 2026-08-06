package nl.hauntedmc.serverfeatures.features.combattag.source;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import org.bukkit.Server;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatSourceResolverTest {

    @Test
    void ignoredProjectileProducesNoCombatSource() {
        Projectile projectile = mock(Projectile.class);
        when(projectile.getType()).thenReturn(EntityType.EGG);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of(EntityType.EGG));

        assertTrue(resolver.resolve(projectile).isEmpty());
    }

    @Test
    void ignoredProjectileDoesNotFallBackToThePaperCausingEntity() {
        Player shooter = player("Shooter");
        Projectile projectile = mock(Projectile.class);
        DamageSource damageSource = mock(DamageSource.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(projectile.getType()).thenReturn(EntityType.EGG);
        when(event.getDamageSource()).thenReturn(damageSource);
        when(event.getDamager()).thenReturn(projectile);
        when(damageSource.getDirectEntity()).thenReturn(projectile);
        when(damageSource.getCausingEntity()).thenReturn(shooter);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of(EntityType.EGG));

        assertTrue(resolver.resolve(event).isEmpty());
    }

    @Test
    void entityDamageEventDamagerWinsWhenDamageSourceDirectEntityDiffers() {
        LivingEntity mob = mock(LivingEntity.class);
        Player misleadingDirectEntity = player("Misleading");
        DamageSource damageSource = mock(DamageSource.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        UUID mobId = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(mobId);
        when(mob.getType()).thenReturn(EntityType.ZOMBIE);
        when(mob.getName()).thenReturn("Zombie");
        when(mob.getEntitySpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(event.getDamager()).thenReturn(mob);
        when(event.getDamageSource()).thenReturn(damageSource);
        when(damageSource.getDirectEntity()).thenReturn(misleadingDirectEntity);
        when(damageSource.getCausingEntity()).thenReturn(misleadingDirectEntity);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve(event).orElseThrow();

        assertEquals(mobId, source.damageSourceId());
        assertSame(mob, source.sourceEntity());
        assertEquals(CombatTagReason.MELEE, source.reason());
    }

    @Test
    void paperCausingEntityLinksPlayerCausedExplosionsWithoutARecognizedCarrier() {
        Player player = player("CrystalUser");
        Entity crystal = mock(Entity.class);
        DamageSource damageSource = mock(DamageSource.class);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getDamageSource()).thenReturn(damageSource);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
        when(damageSource.getDirectEntity()).thenReturn(crystal);
        when(damageSource.getCausingEntity()).thenReturn(player);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve(event).orElseThrow();

        assertSame(player, source.player());
        assertSame(player, source.sourceEntity());
        assertEquals(CombatTagReason.EXPLOSION, source.reason());
    }

    @Test
    void projectileLinksToItsPlayerShooter() {
        Player player = player("Archer");
        Projectile projectile = mock(Projectile.class);
        when(projectile.getType()).thenReturn(EntityType.ARROW);
        when(projectile.getShooter()).thenReturn(player);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve(projectile).orElseThrow();

        assertSame(player, source.player());
        assertSame(player, source.sourceEntity());
        assertEquals(player.getUniqueId(), source.opponent().uniqueId());
        assertEquals(CombatTagReason.PROJECTILE, source.reason());
    }

    @Test
    void fireworkUsesItsSpawningPlayerInsteadOfGenericProjectileHandling() {
        Player owner = player("Rocketeer");
        Server server = mock(Server.class);
        Firework firework = mock(Firework.class);
        UUID ownerId = owner.getUniqueId();
        when(firework.getType()).thenReturn(EntityType.FIREWORK_ROCKET);
        when(firework.getSpawningEntity()).thenReturn(ownerId);
        when(firework.getServer()).thenReturn(server);
        when(server.getPlayer(ownerId)).thenReturn(owner);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve(firework).orElseThrow();

        assertSame(owner, source.player());
        assertSame(owner, source.sourceEntity());
        assertEquals(ownerId, source.opponent().uniqueId());
        assertEquals(CombatTagReason.FIREWORK, source.reason());
    }

    @Test
    void disabledProjectileLinkingAlsoIgnoresFireworks() {
        Firework firework = mock(Firework.class);
        when(firework.getType()).thenReturn(EntityType.FIREWORK_ROCKET);
        CombatSourceResolver resolver = resolver(true, false, true, Set.of());

        assertTrue(resolver.resolve(firework).isEmpty());
    }

    @Test
    void directMobSourceRetainsItsPersistentSpawnReason() {
        LivingEntity mob = mock(LivingEntity.class);
        UUID mobId = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(mobId);
        when(mob.getType()).thenReturn(EntityType.ZOMBIE);
        when(mob.getName()).thenReturn("Zombie");
        when(mob.getEntitySpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.SPAWNER);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve(mob).orElseThrow();

        assertEquals(mobId, source.damageSourceId());
        assertSame(mob, source.sourceEntity());
        assertEquals(CreatureSpawnEvent.SpawnReason.SPAWNER, source.spawnReason());
    }

    @Test
    void tamedPetLinksTheAttackerToItsOwnerOnly() {
        Player owner = player("Owner");
        Tameable pet = mock(Tameable.class);
        UUID petId = UUID.randomUUID();
        when(pet.getUniqueId()).thenReturn(petId);
        when(pet.getType()).thenReturn(EntityType.WOLF);
        when(pet.getName()).thenReturn("Wolf");
        when(pet.getOwner()).thenReturn(owner);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve((Entity) pet).orElseThrow();

        assertSame(owner, source.player());
        assertSame(pet, source.sourceEntity());
        assertEquals(owner.getUniqueId(), source.opponent().uniqueId());
        assertEquals(owner.getUniqueId(), source.damageSourceId());
        assertEquals(CombatTagReason.PET, source.reason());
    }

    @Test
    void disabledTntLinkingIgnoresPrimedTnt() {
        TNTPrimed tnt = mock(TNTPrimed.class);
        Player primer = player("Primer");
        when(tnt.getSource()).thenReturn(primer);
        CombatSourceResolver resolver = resolver(true, true, false, Set.of());

        assertTrue(resolver.resolve(tnt).isEmpty());
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static CombatSourceResolver resolver(
            boolean pets,
            boolean projectiles,
            boolean tnt,
            Set<EntityType> ignored
    ) {
        return new CombatSourceResolver(
                new CombatTagSettings.AttributionSettings(
                        pets,
                        new CombatTagSettings.ProjectileSettings(projectiles, ignored),
                        true,
                        tnt,
                        Set.of()
                )
        );
    }
}
