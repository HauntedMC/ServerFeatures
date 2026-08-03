package nl.hauntedmc.serverfeatures.features.limitspawners.command;

import nl.hauntedmc.serverfeatures.api.command.FeatureCommand;
import nl.hauntedmc.serverfeatures.api.command.meta.CommandMeta;
import nl.hauntedmc.serverfeatures.features.limitspawners.LimitSpawners;
import nl.hauntedmc.serverfeatures.features.limitspawners.internal.LimitSpawnersHandler;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.LimitMetric;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LimitSpawnersCommand extends FeatureCommand {

    private static final String STATS_PERMISSION =
            "serverfeatures.feature.limitspawners.command.stats";
    private static final String INSPECT_PERMISSION =
            "serverfeatures.feature.limitspawners.command.inspect";
    private static final String CLEANUP_PERMISSION =
            "serverfeatures.feature.limitspawners.command.cleanup";
    private static final String RESCAN_PERMISSION =
            "serverfeatures.feature.limitspawners.command.rescan";

    private final LimitSpawners feature;
    private final LimitSpawnersHandler handler;

    public LimitSpawnersCommand(LimitSpawners feature, LimitSpawnersHandler handler) {
        super(new CommandMeta.Builder("limitspawners").build());
        this.feature = feature;
        this.handler = handler;
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String label,
            String @NotNull [] args
    ) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> stats(sender);
            case "inspect" -> inspect(sender);
            case "cleanup" -> cleanup(sender, args);
            case "rescan" -> rescan(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean stats(CommandSender sender) {
        if (!requirePermission(sender, STATS_PERMISSION)) {
            return true;
        }

        LimitSpawnersHandler.StatsSnapshot snapshot = handler.stats();
        sender.sendMessage(message("limitspawners.command.stats_header", sender)
                .with("active", snapshot.activeMobs())
                .with("spawners", snapshot.indexedSpawners())
                .build());

        for (var entry : snapshot.worldCounts().entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            sender.sendMessage(message("limitspawners.command.stats_world", sender)
                    .with("world", world == null ? entry.getKey() : world.getName())
                    .with("count", entry.getValue())
                    .build());
        }
        for (LimitMetric metric : LimitMetric.values()) {
            long count = snapshot.metrics().getOrDefault(metric, 0L);
            if (count > 0) {
                sender.sendMessage(message("limitspawners.command.stats_metric", sender)
                        .with("metric", metric.name().toLowerCase(Locale.ROOT))
                        .with("count", count)
                        .build());
            }
        }
        return true;
    }

    private boolean inspect(CommandSender sender) {
        if (!requirePermission(sender, INSPECT_PERMISSION)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("limitspawners.command.players_only", sender).build());
            return true;
        }

        Block target = player.getTargetBlockExact(8);
        if (target == null || target.getType() != Material.SPAWNER) {
            sender.sendMessage(message("limitspawners.command.no_target", sender).build());
            return true;
        }

        SpawnerKey source = SpawnerKey.of(target.getLocation());
        handler.inspect(source, player).ifPresentOrElse(inspection -> {
            sender.sendMessage(message("limitspawners.command.inspect_header", sender)
                    .with("world", target.getWorld().getName())
                    .with("x", source.x())
                    .with("y", source.y())
                    .with("z", source.z())
                    .with("type", String.valueOf(inspection.entityType()))
                    .build());
            sender.sendMessage(message("limitspawners.command.inspect_counts", sender)
                    .with("active", inspection.activeCount())
                    .with("source_limit", inspection.sourceLimit())
                    .with("area", inspection.areaCount())
                    .with("area_limit", inspection.areaLimit())
                    .with("spawners", inspection.nearbySpawnerCount())
                    .with("placement_limit", inspection.placementLimit())
                    .build());
            sender.sendMessage(message("limitspawners.command.inspect_state", sender)
                    .with("disabled", inspection.disabled())
                    .with("activated", inspection.activated())
                    .with("delay", inspection.delay())
                    .with("min_delay", inspection.minimumDelay())
                    .with("max_delay", inspection.maximumDelay())
                    .with("spawn_count", inspection.spawnCount())
                    .with("player_range", inspection.requiredPlayerRange())
                    .with("spawn_range", inspection.spawnRange())
                    .with("nearby_limit", inspection.maxNearbyEntities())
                    .build());
            for (LimitSpawnersHandler.TrackedEntityView entity : inspection.entities()) {
                sender.sendMessage(message("limitspawners.command.inspect_entity", sender)
                        .with("uuid", entity.entityId())
                        .with("type", entity.entityType())
                        .with("age", entity.ageSeconds())
                        .with("distance", String.format(Locale.ROOT, "%.1f", entity.distanceFromSource()))
                        .build());
            }
        }, () -> sender.sendMessage(
                message("limitspawners.command.no_target", sender).build()
        ));
        return true;
    }

    private boolean cleanup(CommandSender sender, String[] args) {
        if (!requirePermission(sender, CLEANUP_PERMISSION)) {
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        int removed;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "spawner" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(message("limitspawners.command.players_only", sender).build());
                    return true;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null || target.getType() != Material.SPAWNER) {
                    sender.sendMessage(message("limitspawners.command.no_target", sender).build());
                    return true;
                }
                SpawnerKey source = SpawnerKey.of(target.getLocation());
                int before = handler.inspect(source, player)
                        .map(LimitSpawnersHandler.SpawnerInspection::activeCount)
                        .orElse(0);
                handler.cleanupSource(source, LimitMetric.PLUGIN_REMOVAL);
                removed = before;
            }
            case "radius" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    sendUsage(sender);
                    return true;
                }
                int radius;
                try {
                    radius = Integer.parseInt(args[2]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(message("limitspawners.command.invalid_radius", sender).build());
                    return true;
                }
                if (radius < 1) {
                    sender.sendMessage(message("limitspawners.command.invalid_radius", sender).build());
                    return true;
                }
                removed = handler.cleanupRadius(player.getLocation(), radius);
            }
            case "world" -> {
                World world;
                if (args.length >= 3) {
                    world = Bukkit.getWorld(args[2]);
                } else if (sender instanceof Player player) {
                    world = player.getWorld();
                } else {
                    sendUsage(sender);
                    return true;
                }
                if (world == null) {
                    sender.sendMessage(message("limitspawners.command.world_not_found", sender).build());
                    return true;
                }
                removed = handler.cleanupWorld(world.getUID());
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }

        sender.sendMessage(message("limitspawners.command.cleanup_success", sender)
                .with("count", removed)
                .build());
        return true;
    }

    private boolean rescan(CommandSender sender, String[] args) {
        if (!requirePermission(sender, RESCAN_PERMISSION)) {
            return true;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("loaded")) {
            sendUsage(sender);
            return true;
        }

        int changed = handler.rescanLoadedChunks();
        sender.sendMessage(message("limitspawners.command.rescan_success", sender)
                .with("count", changed)
                .build());
        return true;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(feature.getLocalizationHandler()
                .getMessage("general.no_permission")
                .forAudience(sender)
                .build());
        return false;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(message("limitspawners.command.usage", sender).build());
    }

    private nl.hauntedmc.serverfeatures.api.io.localization.MessageBuilder message(
            String key,
            CommandSender sender
    ) {
        return feature.getLocalizationHandler().getMessage(key).forAudience(sender);
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            String @NotNull [] args
    ) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            addIfPermitted(options, sender, STATS_PERMISSION, "stats");
            addIfPermitted(options, sender, INSPECT_PERMISSION, "inspect");
            addIfPermitted(options, sender, CLEANUP_PERMISSION, "cleanup");
            addIfPermitted(options, sender, RESCAN_PERMISSION, "rescan");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cleanup")) {
            return List.of("spawner", "radius", "world").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rescan")) {
            return "loaded".startsWith(args[1].toLowerCase(Locale.ROOT))
                    ? List.of("loaded")
                    : List.of();
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("cleanup")
                && args[1].equalsIgnoreCase("world")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private static void addIfPermitted(
            List<String> options,
            CommandSender sender,
            String permission,
            String value
    ) {
        if (sender.hasPermission(permission)) {
            options.add(value);
        }
    }
}
