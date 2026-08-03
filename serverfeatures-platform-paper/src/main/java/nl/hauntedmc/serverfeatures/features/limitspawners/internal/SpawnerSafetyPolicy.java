package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.config.LimitSpawnersConfig;
import org.bukkit.block.CreatureSpawner;

import java.util.Objects;

/**
 * Normalizes only unsafe spawner settings and leaves safer custom values intact.
 */
public final class SpawnerSafetyPolicy {

    private final LimitSpawnersConfig.SpawnerSafety config;

    public SpawnerSafetyPolicy(LimitSpawnersConfig.SpawnerSafety config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public boolean apply(CreatureSpawner spawner) {
        Objects.requireNonNull(spawner, "spawner");
        if (!config.enabled()) {
            return false;
        }

        boolean changed = false;

        if (spawner.getSpawnCount() > config.maxSpawnCount()) {
            spawner.setSpawnCount(config.maxSpawnCount());
            changed = true;
        }

        int minimumDelay = Math.max(spawner.getMinSpawnDelay(), config.minimumSpawnDelayTicks());
        if (spawner.getMaxSpawnDelay() < minimumDelay) {
            spawner.setMaxSpawnDelay(minimumDelay);
            changed = true;
        }
        if (spawner.getMinSpawnDelay() != minimumDelay) {
            spawner.setMinSpawnDelay(minimumDelay);
            changed = true;
        }

        int requiredRange = spawner.getRequiredPlayerRange();
        if (requiredRange <= 0 || requiredRange > config.maxRequiredPlayerRange()) {
            spawner.setRequiredPlayerRange(config.maxRequiredPlayerRange());
            changed = true;
        }

        if (spawner.getSpawnRange() > config.maxSpawnRange()) {
            spawner.setSpawnRange(config.maxSpawnRange());
            changed = true;
        }

        if (spawner.getMaxNearbyEntities() > config.maxNearbyEntities()) {
            spawner.setMaxNearbyEntities(config.maxNearbyEntities());
            changed = true;
        }

        if (changed) {
            spawner.update(true, false);
        }
        return changed;
    }
}
