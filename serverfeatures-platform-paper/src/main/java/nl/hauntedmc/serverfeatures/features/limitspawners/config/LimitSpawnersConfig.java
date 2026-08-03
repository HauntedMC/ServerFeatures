package nl.hauntedmc.serverfeatures.features.limitspawners.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Validated immutable settings for the complete LimitSpawners feature.
 */
public record LimitSpawnersConfig(
        int farmRadius,
        MobControl mobControl,
        PlacementControl placementControl,
        SpawnerSafety spawnerSafety,
        PositionIndex positionIndex,
        boolean blockSpawnerMinecarts
) {

    private static final int MINIMUM_TASK_INTERVAL_TICKS = 20;

    public LimitSpawnersConfig {
        Objects.requireNonNull(mobControl, "mobControl");
        Objects.requireNonNull(placementControl, "placementControl");
        Objects.requireNonNull(spawnerSafety, "spawnerSafety");
        Objects.requireNonNull(positionIndex, "positionIndex");
    }

    public static LimitSpawnersConfig load(LimitSpawners feature) {
        Objects.requireNonNull(feature, "feature");
        ConfigNode root = feature.getConfigHandler().node();

        ConfigNode safetyNode = root.get("spawner_safety");
        SpawnerSafety safety = new SpawnerSafety(
                safetyNode.get("enabled").as(Boolean.class, true),
                nonNegative(safetyNode, "max_spawn_count", 4),
                positive(safetyNode, "minimum_spawn_delay_ticks", 200),
                positive(safetyNode, "max_required_player_range", 16),
                nonNegative(safetyNode, "max_spawn_range", 4),
                nonNegative(safetyNode, "max_nearby_entities", 6)
        );

        int configuredRadius = positive(root, "farm_radius", 32);
        int minimumRadius = Math.max(1, safety.maxRequiredPlayerRange() * 2);
        int farmRadius = Math.max(configuredRadius, minimumRadius);
        if (farmRadius != configuredRadius) {
            feature.getLogger().warning(
                    "farm_radius was raised to " + farmRadius
                            + " so it remains at least twice max_required_player_range."
            );
        }

        ConfigNode mobNode = root.get("mob_control");
        int perWorldLimit = nonNegative(mobNode, "per_world_limit", 256);
        int configuredServerLimit = nonNegative(mobNode, "server_limit", 512);
        int serverLimit = Math.max(perWorldLimit, configuredServerLimit);
        if (serverLimit != configuredServerLimit) {
            feature.getLogger().warning(
                    "mob_control.server_limit was raised to " + serverLimit
                            + " because it cannot be lower than per_world_limit."
            );
        }

        MobControl mobControl = new MobControl(
                nonNegative(mobNode, "per_spawner_limit", 4),
                nonNegative(mobNode, "per_area_limit", 16),
                perWorldLimit,
                serverLimit,
                positive(mobNode, "blocked_retry_delay_ticks", 200),
                atLeast(
                        mobNode,
                        "maintenance_interval_ticks",
                        100,
                        MINIMUM_TASK_INTERVAL_TICKS
                ),
                nonNegative(mobNode, "outside_radius_grace_seconds", 30),
                nonNegative(mobNode, "inactive_source_grace_seconds", 30),
                nonNegative(mobNode, "maximum_lifetime_seconds", 0),
                parseTypeOverrides(feature, mobNode.get("type_overrides"))
        );

        ConfigNode placementNode = root.get("placement_control");
        int hardLimit = nonNegative(placementNode, "hard_limit", 6);
        int defaultLimit = Math.min(
                hardLimit,
                nonNegative(placementNode, "default_limit", 2)
        );
        PlacementControl placementControl = new PlacementControl(
                placementNode.get("enabled").as(Boolean.class, true),
                defaultLimit,
                hardLimit,
                placementNode.get("bypass_soft_limit_permission").as(
                        String.class,
                        "serverfeatures.feature.limitspawners.placement.bypass"
                ),
                placementNode.get("bypass_hard_limit_permission").as(
                        String.class,
                        "serverfeatures.feature.limitspawners.placement.hardbypass"
                ),
                parseTiers(feature, placementNode.get("tiers"), hardLimit)
        );

        ConfigNode indexNode = root.get("position_index");
        PositionIndex positionIndex = new PositionIndex(
                atLeast(
                        indexNode,
                        "save_debounce_ticks",
                        20,
                        MINIMUM_TASK_INTERVAL_TICKS
                )
        );

        return new LimitSpawnersConfig(
                farmRadius,
                mobControl,
                placementControl,
                safety,
                positionIndex,
                root.get("block_spawner_minecarts").as(Boolean.class, true)
        );
    }

    private static Map<EntityType, Integer> parseTypeOverrides(
            LimitSpawners feature,
            ConfigNode node
    ) {
        Map<EntityType, Integer> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : node.children().entrySet()) {
            String configuredType = entry.getKey().trim().toUpperCase(Locale.ROOT);
            try {
                EntityType type = EntityType.valueOf(configuredType);
                if (!type.isAlive()) {
                    feature.getLogger().warning(
                            "Ignoring non-living mob_control.type_overrides entry: " + entry.getKey()
                    );
                    continue;
                }
                overrides.put(type, Math.max(0, entry.getValue().as(Integer.class, 0)));
            } catch (IllegalArgumentException exception) {
                feature.getLogger().warning(
                        "Ignoring unknown mob_control.type_overrides entity type: " + entry.getKey()
                );
            }
        }
        return Map.copyOf(overrides);
    }

    private static List<PermissionTier> parseTiers(
            LimitSpawners feature,
            ConfigNode node,
            int hardLimit
    ) {
        List<PermissionTier> tiers = new ArrayList<>();
        for (Map.Entry<String, ConfigNode> entry : node.children().entrySet()) {
            ConfigNode tierNode = entry.getValue();
            String permission = tierNode.get("permission").as(String.class, "").trim();
            if (permission.isEmpty()) {
                feature.getLogger().warning(
                        "Ignoring placement_control.tiers." + entry.getKey()
                                + " because its permission is empty."
                );
                continue;
            }
            int limit = Math.min(
                    hardLimit,
                    Math.max(0, tierNode.get("limit").as(Integer.class, 0))
            );
            tiers.add(new PermissionTier(permission, limit));
        }
        return List.copyOf(tiers);
    }

    private static int positive(ConfigNode node, String key, int fallback) {
        return Math.max(1, node.get(key).as(Integer.class, fallback));
    }

    private static int atLeast(ConfigNode node, String key, int fallback, int minimum) {
        return Math.max(minimum, node.get(key).as(Integer.class, fallback));
    }

    private static int nonNegative(ConfigNode node, String key, int fallback) {
        return Math.max(0, node.get(key).as(Integer.class, fallback));
    }

    public record MobControl(
            int perSpawnerLimit,
            int perAreaLimit,
            int perWorldLimit,
            int serverLimit,
            int blockedRetryDelayTicks,
            int maintenanceIntervalTicks,
            int outsideRadiusGraceSeconds,
            int inactiveSourceGraceSeconds,
            int maximumLifetimeSeconds,
            Map<EntityType, Integer> typeOverrides
    ) {
        public MobControl {
            typeOverrides = Map.copyOf(Objects.requireNonNull(typeOverrides, "typeOverrides"));
        }

        public int perSpawnerLimit(EntityType type) {
            return typeOverrides.getOrDefault(type, perSpawnerLimit);
        }
    }

    public record PlacementControl(
            boolean enabled,
            int defaultLimit,
            int hardLimit,
            String bypassSoftLimitPermission,
            String bypassHardLimitPermission,
            List<PermissionTier> tiers
    ) {
        public PlacementControl {
            bypassSoftLimitPermission = Objects.requireNonNull(
                    bypassSoftLimitPermission,
                    "bypassSoftLimitPermission"
            );
            bypassHardLimitPermission = Objects.requireNonNull(
                    bypassHardLimitPermission,
                    "bypassHardLimitPermission"
            );
            tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers"));
        }
    }

    public record PermissionTier(String permission, int limit) {
        public PermissionTier {
            Objects.requireNonNull(permission, "permission");
        }
    }

    public record SpawnerSafety(
            boolean enabled,
            int maxSpawnCount,
            int minimumSpawnDelayTicks,
            int maxRequiredPlayerRange,
            int maxSpawnRange,
            int maxNearbyEntities
    ) {
    }

    public record PositionIndex(int saveDebounceTicks) {
    }
}
