package nl.hauntedmc.serverfeatures.features.fairperks.policy;

import nl.hauntedmc.serverfeatures.features.fairperks.config.FairPerksSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public final class HostileEntityClassifier {

    private static final NamespacedKey SPAWNER_MOB_KEY =
            new NamespacedKey("serverfeatures", "fairperks_spawnermob");
    private static final byte TRUE = 1;

    private final FairPerksSettings.HostileSettings settings;

    public HostileEntityClassifier(FairPerksSettings.HostileSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public boolean isHostile(Entity entity) {
        if (entity == null || settings.exclude().contains(entity.getType())) {
            return false;
        }
        return settings.include().contains(entity.getType())
                || entity instanceof Enemy;
    }

    public boolean hasNearbyHostile(Player player, int horizontalRadius, int verticalRadius) {
        if (horizontalRadius <= 0 || verticalRadius < 0) {
            return false;
        }
        return player.getNearbyEntities(
                horizontalRadius,
                verticalRadius,
                horizontalRadius
        ).stream().anyMatch(this::isHostile);
    }

    public boolean hasNearbyHostileTargeting(Player player, int horizontalRadius, int verticalRadius) {
        if (horizontalRadius <= 0 || verticalRadius < 0) {
            return false;
        }
        return player.getNearbyEntities(
                horizontalRadius,
                verticalRadius,
                horizontalRadius
        ).stream().anyMatch(entity -> entity instanceof Mob mob
                && isHostile(mob)
                && player.equals(mob.getTarget()));
    }

    public boolean isExemptSpawnerMob(Entity entity) {
        if (!settings.spawnerMobsExempt()) {
            return false;
        }
        return isMarked(entity, SPAWNER_MOB_KEY);
    }

    public void markSpawnerMob(Entity entity) {
        entity.getPersistentDataContainer().set(SPAWNER_MOB_KEY, PersistentDataType.BYTE, TRUE);
    }

    private static boolean isMarked(Entity entity, NamespacedKey key) {
        try {
            Byte marker = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
            return marker != null && marker == TRUE;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
