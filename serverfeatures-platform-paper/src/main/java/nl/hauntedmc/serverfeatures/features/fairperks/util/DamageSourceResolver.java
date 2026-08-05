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
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                Player owner = resolvePlayer(shooterEntity);
                if (owner != null) {
                    return owner;
                }
            }
            Player owner = resolveOnlinePlayer(projectile, projectile.getOwnerUniqueId());
            if (owner != null) {
                return owner;
            }
        }
        if (source instanceof TNTPrimed tnt && tnt.getSource() instanceof Entity owner) {
            return resolvePlayer(owner);
        }
        if (source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity owner) {
            return resolvePlayer(owner);
        }
        if (source instanceof EvokerFangs fangs) {
            LivingEntity owner = fangs.getOwner();
            if (owner != null) {
                return resolvePlayer(owner);
            }
        }
        if (source instanceof Tameable tameable) {
            return resolveTameableOwner(source, tameable);
        }
        if (source instanceof Firework firework) {
            return resolveOnlinePlayer(source, firework.getSpawningEntity());
        }
        return null;
    }

    /**
     * Resolves an online player only when the damage chain originated from their tamed pet.
     */
    public static Player resolveTamedOwner(Entity source) {
        if (source instanceof Tameable tameable) {
            return resolveTameableOwner(source, tameable);
        }
        if (source instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            return resolveTamedOwner(shooter);
        }
        if (source instanceof TNTPrimed tnt && tnt.getSource() instanceof Entity owner) {
            return resolveTamedOwner(owner);
        }
        if (source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity owner) {
            return resolveTamedOwner(owner);
        }
        if (source instanceof EvokerFangs fangs && fangs.getOwner() != null) {
            return resolveTamedOwner(fangs.getOwner());
        }
        return null;
    }

    private static Player resolveTameableOwner(Entity source, Tameable tameable) {
        if (tameable.getOwner() instanceof Player player) {
            return player;
        }
        return resolveOnlinePlayer(source, tameable.getOwnerUniqueId());
    }

    private static Player resolveOnlinePlayer(Entity entity, UUID playerId) {
        return playerId == null ? null : entity.getServer().getPlayer(playerId);
    }
}
