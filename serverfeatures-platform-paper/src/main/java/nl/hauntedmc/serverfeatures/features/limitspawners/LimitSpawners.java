package nl.hauntedmc.serverfeatures.features.limitspawners;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.limitspawners.command.LimitSpawnersCommand;
import nl.hauntedmc.serverfeatures.features.limitspawners.config.LimitSpawnersConfig;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.PendingSpawnerPlacements;
import nl.hauntedmc.serverfeatures.features.limitspawners.listener.LimitSpawnersListener;
import nl.hauntedmc.serverfeatures.features.limitspawners.listener.TransformListener;
import nl.hauntedmc.serverfeatures.features.limitspawners.meta.Meta;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class LimitSpawners extends BukkitBaseFeature<Meta> {

    private LimitSpawnersHandler handler;

    public LimitSpawners(FeatureContext<Meta> context) {
        super(context);
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap config = new ConfigMap();
        config.put("enabled", false);
        config.put("farm_radius", 32);
        config.put("block_spawner_minecarts", true);

        Map<String, Object> mobControl = new LinkedHashMap<>();
        mobControl.put("per_spawner_limit", 4);
        mobControl.put("per_area_limit", 16);
        mobControl.put("per_world_limit", 256);
        mobControl.put("server_limit", 512);
        mobControl.put("blocked_retry_delay_ticks", 200);
        mobControl.put("maintenance_interval_ticks", 100);
        mobControl.put("outside_radius_grace_seconds", 30);
        mobControl.put("inactive_source_grace_seconds", 30);
        mobControl.put("maximum_lifetime_seconds", 0);
        mobControl.put("type_overrides", Map.of());
        config.put("mob_control", mobControl);

        Map<String, Object> tiers = new LinkedHashMap<>();
        tiers.put("tier_1", permissionTier(
                "serverfeatures.feature.limitspawners.placement.tier1",
                3
        ));
        tiers.put("tier_2", permissionTier(
                "serverfeatures.feature.limitspawners.placement.tier2",
                4
        ));
        tiers.put("tier_3", permissionTier(
                "serverfeatures.feature.limitspawners.placement.tier3",
                5
        ));
        tiers.put("tier_4", permissionTier(
                "serverfeatures.feature.limitspawners.placement.tier4",
                6
        ));

        Map<String, Object> placementControl = new LinkedHashMap<>();
        placementControl.put("enabled", true);
        placementControl.put("default_limit", 2);
        placementControl.put("hard_limit", 6);
        placementControl.put(
                "bypass_soft_limit_permission",
                "serverfeatures.feature.limitspawners.placement.bypass"
        );
        placementControl.put(
                "bypass_hard_limit_permission",
                "serverfeatures.feature.limitspawners.placement.hardbypass"
        );
        placementControl.put("tiers", tiers);
        config.put("placement_control", placementControl);

        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("enabled", true);
        safety.put("max_spawn_count", 4);
        safety.put("minimum_spawn_delay_ticks", 200);
        safety.put("max_required_player_range", 16);
        safety.put("max_spawn_range", 4);
        safety.put("max_nearby_entities", 6);
        config.put("spawner_safety", safety);

        config.put("position_index", Map.of("save_debounce_ticks", 20));
        return config;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add(
                "limitspawners.placement_blocked",
                "&cJe kunt hier geen extra spawner plaatsen. Binnen {radius} blokken staan al "
                        + "&f{count}&c spawners; jouw limiet is &f{limit}&c."
        );
        messages.add(
                "limitspawners.command.usage",
                "&eGebruik: /limitspawners <stats|inspect|cleanup|rescan>"
        );
        messages.add(
                "limitspawners.command.players_only",
                "&cDit commando kan alleen door een speler worden gebruikt."
        );
        messages.add(
                "limitspawners.command.no_target",
                "&cKijk naar een spawner binnen acht blokken."
        );
        messages.add(
                "limitspawners.command.invalid_radius",
                "&cDe radius moet een positief geheel getal zijn."
        );
        messages.add("limitspawners.command.world_not_found", "&cDie wereld bestaat niet.");
        messages.add(
                "limitspawners.command.stats_header",
                "&6&lLimitSpawners &7- actief: &f{active}&7, geïndexeerde spawners: &f{spawners}"
        );
        messages.add(
                "limitspawners.command.stats_world",
                "&7Wereld &f{world}&7: &f{count} &7actieve mobs"
        );
        messages.add("limitspawners.command.stats_metric", "&7{metric}: &f{count}");
        messages.add(
                "limitspawners.command.inspect_header",
                "&6Spawner &f{world} {x} {y} {z} &7(type: &f{type}&7)"
        );
        messages.add(
                "limitspawners.command.inspect_counts",
                "&7Mobs: &f{active}/{source_limit}&7, gebied: &f{area}/{area_limit}&7, "
                        + "spawners: &f{spawners}/{placement_limit}"
        );
        messages.add(
                "limitspawners.command.inspect_state",
                "&7Uitgeschakeld: &f{disabled}&7, actief: &f{activated}&7, delay: &f{delay} "
                        + "&7({min_delay}-{max_delay}), spawnCount: &f{spawn_count}&7, "
                        + "playerRange: &f{player_range}&7, spawnRange: &f{spawn_range}&7, "
                        + "nearbyLimit: &f{nearby_limit}"
        );
        messages.add(
                "limitspawners.command.inspect_entity",
                "&8- &f{uuid} &7{type}, leeftijd &f{age}s&7, afstand &f{distance}"
        );
        messages.add(
                "limitspawners.command.cleanup_success",
                "&a{count} actieve spawner-mobs verwijderd."
        );
        messages.add(
                "limitspawners.command.rescan_success",
                "&aGeladen chunks opnieuw gescand; {count} indexwijzigingen."
        );
        return messages;
    }

    @Override
    public void initialize() {
        PendingSpawnerPlacements.clearAll();
        LimitSpawnersConfig config = LimitSpawnersConfig.load(this);
        this.handler = new LimitSpawnersHandler(this, config);
        getLifecycleManager().getListenerManager().registerListener(new TransformListener(handler));
        getLifecycleManager().getListenerManager().registerListener(
                new LimitSpawnersListener(this, handler)
        );
        getLifecycleManager().getCommandManager().registerFeatureCommand(
                new LimitSpawnersCommand(this, handler)
        );
        handler.start();
    }

    @Override
    public void disable() {
        try {
            if (handler != null && !PendingSpawnerPlacements.isEmpty()) {
                PendingSpawnerPlacements.commitAll();
                handler.rescanLoadedChunks();
            }
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Could not reconcile pending spawner placements before shutdown.",
                    exception
            );
        } finally {
            PendingSpawnerPlacements.clearAll();
        }

        if (handler != null) {
            handler.shutdown();
        }
    }

    public LimitSpawnersHandler getHandler() {
        return handler;
    }

    private static Map<String, Object> permissionTier(String permission, int limit) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("permission", permission);
        tier.put("limit", limit);
        return tier;
    }
}
