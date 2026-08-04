package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.config.LimitSpawnersConfig;
import org.bukkit.permissions.Permissible;

import java.util.Objects;

/**
 * Resolves rank permissions without a direct permissions-plugin dependency.
 */
public final class SpawnerLimitResolver {

    private final LimitSpawnersConfig config;

    public SpawnerLimitResolver(LimitSpawnersConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public PlacementLimit placementLimit(Permissible permissible) {
        Objects.requireNonNull(permissible, "permissible");
        LimitSpawnersConfig.PlacementControl placement = config.placementControl();

        if (hasPermission(permissible, placement.bypassHardLimitPermission())) {
            return new PlacementLimit(Integer.MAX_VALUE, true);
        }

        int resolved = placement.defaultLimit();
        if (hasPermission(permissible, placement.bypassSoftLimitPermission())) {
            resolved = placement.hardLimit();
        }

        for (LimitSpawnersConfig.PermissionTier tier : placement.tiers()) {
            if (hasPermission(permissible, tier.permission())) {
                resolved = Math.max(resolved, tier.limit());
            }
        }

        return new PlacementLimit(Math.min(resolved, placement.hardLimit()), false);
    }

    private static boolean hasPermission(Permissible permissible, String permission) {
        return permission != null
                && !permission.isBlank()
                && permissible.hasPermission(permission);
    }

    public record PlacementLimit(int limit, boolean hardBypass) {

        public boolean permits(int existingNearby) {
            return hardBypass || existingNearby < limit;
        }
    }
}
