package nl.hauntedmc.serverfeatures.features.fairperks.util;

import org.bukkit.Server;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DamageSourceResolverTest {

    @Test
    void resolvesProjectileOwnerFromOwnerUuid() {
        Projectile projectile = mock(Projectile.class);
        ProjectileSource shooter = mock(ProjectileSource.class);
        Server server = mock(Server.class);
        Player owner = mock(Player.class);
        UUID ownerId = UUID.randomUUID();

        when(projectile.getShooter()).thenReturn(shooter);
        when(projectile.getOwnerUniqueId()).thenReturn(ownerId);
        when(projectile.getServer()).thenReturn(server);
        when(server.getPlayer(ownerId)).thenReturn(owner);

        assertSame(owner, DamageSourceResolver.resolvePlayer(projectile));
    }

    @Test
    void resolvesEvokerFangsOwner() {
        EvokerFangs fangs = mock(EvokerFangs.class);
        Player owner = mock(Player.class);
        when(fangs.getOwner()).thenReturn(owner);

        assertSame(owner, DamageSourceResolver.resolvePlayer(fangs));
    }

    @Test
    void resolvesTameableOwnerFromOwnerUuid() {
        Tameable tameable = mock(Tameable.class);
        Server server = mock(Server.class);
        Player owner = mock(Player.class);
        UUID ownerId = UUID.randomUUID();

        when(tameable.getOwner()).thenReturn(null);
        when(tameable.getOwnerUniqueId()).thenReturn(ownerId);
        when(tameable.getServer()).thenReturn(server);
        when(server.getPlayer(ownerId)).thenReturn(owner);

        assertSame(owner, DamageSourceResolver.resolvePlayer(tameable));
    }

    @Test
    void resolvesFireworkSpawner() {
        Firework firework = mock(Firework.class);
        Server server = mock(Server.class);
        Player owner = mock(Player.class);
        UUID ownerId = UUID.randomUUID();

        when(firework.getSpawningEntity()).thenReturn(ownerId);
        when(firework.getServer()).thenReturn(server);
        when(server.getPlayer(ownerId)).thenReturn(owner);

        assertSame(owner, DamageSourceResolver.resolvePlayer(firework));
    }

    @Test
    void returnsNullWhenNoOwnerCanBeResolved() {
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(null);
        when(projectile.getOwnerUniqueId()).thenReturn(null);

        assertNull(DamageSourceResolver.resolvePlayer(projectile));
    }
}
