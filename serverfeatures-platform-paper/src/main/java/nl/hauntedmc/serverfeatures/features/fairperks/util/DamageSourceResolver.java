package nl.hauntedmc.serverfeatures.features.fairperks.util;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Resolves the online player responsible for direct and indirect entity actions.
 */
public final class DamageSourceResolver {

    private DamageSourceResolver() {
    }

    public static Player resolvePlayer(Entity source) {
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof Projectile projectile) {
            Player owner = resolveProjectileOwner(projectile);
            if (owner != null) {
                return owner;
            }
        }
        if (source instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        if (source instanceof EvokerFangs fangs) {
            LivingEntity owner = fangs.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
        }
        if (source instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player player) {
                return player;
            }
            Player owner = resolveOnlinePlayer(source, tameable.getOwnerUniqueId());
            if (owner != null) {
                return owner;
            }
        }
        if (source instanceof Firework firework) {
            return resolveOnlinePlayer(source, firework.getSpawningEntity());
        }
        return null;
    }

    private static Player resolveProjectileOwner(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player player) {
            return player;
        }
        return resolveOnlinePlayer(projectile, projectile.getOwnerUniqueId());
    }

    private static Player resolveOnlinePlayer(Entity entity, UUID playerId) {
        return playerId == null ? null : entity.getServer().getPlayer(playerId);
    }
}
