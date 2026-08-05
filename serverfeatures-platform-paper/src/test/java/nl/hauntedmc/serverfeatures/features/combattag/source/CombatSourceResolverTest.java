package nl.hauntedmc.serverfeatures.features.combattag.source;

import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
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
    void projectileLinksToItsPlayerShooter() {
        Player player = player("Archer");
        Projectile projectile = mock(Projectile.class);
        when(projectile.getType()).thenReturn(EntityType.ARROW);
        when(projectile.getShooter()).thenReturn(player);
        CombatSourceResolver resolver = resolver(true, true, true, Set.of());

        var source = resolver.resolve(projectile).orElseThrow();

        assertSame(player, source.player());
        assertEquals(player.getUniqueId(), source.opponent().uniqueId());
        assertEquals(CombatTagReason.PROJECTILE, source.reason());
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

        var source = resolver.resolve((org.bukkit.entity.Entity) pet).orElseThrow();

        assertSame(owner, source.player());
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
