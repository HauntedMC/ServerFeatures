package nl.hauntedmc.serverfeatures.features.combattag.source;

import nl.hauntedmc.serverfeatures.api.combat.CombatOpponent;
import nl.hauntedmc.serverfeatures.api.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.features.combattag.config.CombatTagSettings;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CombatSourceResolver {

    private final CombatTagSettings.AttributionSettings settings;

    public CombatSourceResolver(CombatTagSettings.AttributionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Optional<ResolvedCombatSource> resolve(Entity directDamager) {
        return resolve(directDamager, null);
    }

    private Optional<ResolvedCombatSource> resolve(Entity source, CombatTagReason forcedReason) {
        if (source instanceof Player player) {
            return Optional.of(source(
                    opponent(player),
                    player.getUniqueId(),
                    player,
                    forcedReason == null ? CombatTagReason.MELEE : forcedReason,
                    null
            ));
        }

        if (source instanceof Firework firework) {
            CombatTagSettings.ProjectileSettings projectiles = settings.projectiles();
            if (!projectiles.enabled() || projectiles.ignoredTypes().contains(firework.getType())) {
                return Optional.empty();
            }
            Player owner = onlinePlayer(firework, firework.getSpawningEntity());
            return owner == null
                    ? Optional.empty()
                    : Optional.of(source(
                            opponent(owner),
                            owner.getUniqueId(),
                            owner,
                            CombatTagReason.FIREWORK,
                            null
                    ));
        }

        if (source instanceof Projectile projectile) {
            CombatTagSettings.ProjectileSettings projectiles = settings.projectiles();
            if (!projectiles.enabled() || projectiles.ignoredTypes().contains(projectile.getType())) {
                return Optional.empty();
            }
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                return resolve(shooterEntity, CombatTagReason.PROJECTILE);
            }
            Player owner = onlinePlayer(projectile, projectile.getOwnerUniqueId());
            return owner == null
                    ? Optional.empty()
                    : Optional.of(source(
                            opponent(owner),
                            owner.getUniqueId(),
                            owner,
                            CombatTagReason.PROJECTILE,
                            null
                    ));
        }

        if (source instanceof TNTPrimed tnt) {
            if (!settings.linkPrimedTnt() || !(tnt.getSource() instanceof Entity owner)) {
                return Optional.empty();
            }
            return resolve(owner, CombatTagReason.TNT);
        }

        if (source instanceof AreaEffectCloud cloud) {
            ProjectileSource cloudSource = cloud.getSource();
            if (cloudSource instanceof Entity owner) {
                return resolve(owner, CombatTagReason.AREA_EFFECT);
            }
        }

        if (source instanceof EvokerFangs fangs && fangs.getOwner() != null) {
            return resolve(fangs.getOwner(), CombatTagReason.EVOKER_FANGS);
        }

        if (source instanceof Tameable tameable && settings.linkTamedPets()) {
            if (tameable.getOwner() instanceof Player owner) {
                return Optional.of(source(
                        opponent(owner),
                        owner.getUniqueId(),
                        owner,
                        CombatTagReason.PET,
                        null
                ));
            }
            UUID ownerId = tameable.getOwnerUniqueId();
            Player owner = onlinePlayer(source, ownerId);
            if (owner != null) {
                return Optional.of(source(
                        opponent(owner),
                        owner.getUniqueId(),
                        owner,
                        CombatTagReason.PET,
                        null
                ));
            }
            if (ownerId != null) {
                return Optional.of(source(
                        new CombatOpponent(ownerId, EntityType.PLAYER, "pet owner", true),
                        source.getUniqueId(),
                        null,
                        CombatTagReason.PET,
                        source.getEntitySpawnReason()
                ));
            }
        }

        if (source instanceof LivingEntity) {
            return Optional.of(source(
                    opponent(source),
                    source.getUniqueId(),
                    null,
                    forcedReason == null ? CombatTagReason.MELEE : forcedReason,
                    source.getEntitySpawnReason()
            ));
        }
        return Optional.empty();
    }

    private static ResolvedCombatSource source(
            CombatOpponent opponent,
            UUID damageSourceId,
            Player player,
            CombatTagReason reason,
            CreatureSpawnEvent.SpawnReason spawnReason
    ) {
        return new ResolvedCombatSource(opponent, damageSourceId, player, reason, spawnReason);
    }

    public static CombatOpponent opponent(Entity entity) {
        return new CombatOpponent(
                entity.getUniqueId(),
                entity.getType(),
                entity.getName(),
                entity instanceof Player
        );
    }

    private static Player onlinePlayer(Entity entity, UUID playerId) {
        return playerId == null ? null : entity.getServer().getPlayer(playerId);
    }

    public record ResolvedCombatSource(
            CombatOpponent opponent,
            UUID damageSourceId,
            Player player,
            CombatTagReason reason,
            CreatureSpawnEvent.SpawnReason spawnReason
    ) {
        public ResolvedCombatSource {
            Objects.requireNonNull(opponent, "opponent");
            Objects.requireNonNull(damageSourceId, "damageSourceId");
            Objects.requireNonNull(reason, "reason");
        }

        public boolean playerSource() {
            return opponent.player();
        }
    }
}
