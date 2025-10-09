package nl.hauntedmc.serverfeatures.internal.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.tab.TabCompletion;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import nl.hauntedmc.serverfeatures.api.command.tab.filter.Filters;
import nl.hauntedmc.serverfeatures.api.command.tab.provider.Providers;
import nl.hauntedmc.serverfeatures.api.command.tab.sort.Sorters;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.internal.action.disable.FeatureDisableResponse;
import nl.hauntedmc.serverfeatures.internal.action.enable.FeatureEnableResponse;
import nl.hauntedmc.serverfeatures.internal.action.reload.FeatureReloadResponse;
import nl.hauntedmc.serverfeatures.internal.action.softreload.FeatureSoftReloadResponse;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ServerFeaturesCommand implements CommandExecutor, TabCompleter {

    private final ServerFeatures plugin;
    private final TabCompletion tabs;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;

        var loadedFeatures = Providers.dynamic(ctx ->
                plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                        .map(BukkitBaseFeature::getFeatureName)
                        .filter(Objects::nonNull)
                        .toList()
        );

        var availableButNotLoaded = Providers.dynamic(ctx -> {
            Set<String> loadedLower = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                    .map(BukkitBaseFeature::getFeatureName)
                    .filter(Objects::nonNull)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            return plugin.getFeatureLoadManager().getFeatureRegistry().getAvailableFeatures().keySet().stream()
                    .filter(Objects::nonNull)
                    .filter(name -> !loadedLower.contains(name.toLowerCase(Locale.ROOT)))
                    .toList();
        });

        TabTree tree = TabTree.builder()
                .filter(Filters.prefixThenContains())
                .sorter(Sorters.caseInsensitive())
                .root()
                .literal("status", n -> n.require("serverfeatures.command.status"))
                .literal("list", n -> n.require("serverfeatures.command.list"))
                .literal("reloadlocal", n -> n.require("serverfeatures.command.reloadlocal"))

                .literal("softreload", n -> n.require("serverfeatures.command.reload")
                        .child(b -> b.arg("feature", loadedFeatures))
                )
                .literal("reload", n -> n.require("serverfeatures.command.reload")
                        .child(b -> b.arg("feature", loadedFeatures))
                )
                .literal("disable", n -> n.require("serverfeatures.command.disable")
                        .child(b -> b.arg("feature", loadedFeatures))
                )
                .literal("enable", n -> n.require("serverfeatures.command.enable")
                        .child(b -> b.arg("feature", availableButNotLoaded))
                )
                .build();

        this.tabs = TabCompletion.of(tree);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return tabs.complete(sender, alias, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            send("general.usage", sender, Map.of());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                if (!has(sender, "serverfeatures.command.status")) return true;
                sendPluginStatus(sender);
                return true;

            case "list":
                if (!has(sender, "serverfeatures.command.list")) return true;
                listLoadedFeatures(sender);
                return true;

            case "softreload":
                if (!has(sender, "serverfeatures.command.reload")) return true;
                if (args.length < 2) { send("command.softreload.usage", sender, Map.of()); return true; }
                handleSoftReload(sender, args[1]);
                return true;

            case "reload":
                if (!has(sender, "serverfeatures.command.reload")) return true;
                if (args.length < 2) { send("command.reload.usage", sender, Map.of()); return true; }
                handleReload(sender, args[1]);
                return true;

            case "enable":
                if (!has(sender, "serverfeatures.command.enable")) return true;
                if (args.length < 2) return false;
                handleEnable(sender, args[1]);
                return true;

            case "disable":
                if (!has(sender, "serverfeatures.command.disable")) return true;
                if (args.length < 2) return false;
                handleDisable(sender, args[1]);
                return true;

            case "reloadlocal":
                if (!has(sender, "serverfeatures.command.reloadlocal")) return true;
                try {
                    plugin.getLocalizationHandler().reloadLocalization();
                    send("command.reloadlocal.success", sender, Map.of());
                } catch (Throwable t) {
                    plugin.getLogger().warning("Localization reload failed: " + t.getMessage());
                    send("command.reloadlocal.fail", sender, Map.of());
                }
                return true;

            default:
                send("general.unknown_command", sender, Map.of());
                return true;
        }
    }

    private void handleEnable(CommandSender sender, String feature) {
        FeatureEnableResponse resp = plugin.getFeatureLoadManager().enableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> send("command.enable.success", sender, Map.of("feature", feature));
            case NOT_FOUND -> send("command.enable.not_found", sender, Map.of("feature", feature));
            case ALREADY_LOADED -> send("command.enable.already_loaded", sender, Map.of("feature", feature));
            case MISSING_PLUGIN_DEPENDENCY -> {
                String plugins = String.join(", ", resp.missingPlugins());
                send("command.enable.missing_plugin_dependency", sender, Map.of("feature", feature, "plugins", plugins));
            }
            case MISSING_FEATURE_DEPENDENCY -> {
                String deps = String.join(", ", resp.missingFeatures());
                send("command.enable.missing_feature_dependency", sender, Map.of("feature", feature, "features", deps));
            }
            default -> send("command.enable.failed", sender, Map.of("feature", feature));
        }
    }

    private void handleDisable(CommandSender sender, String feature) {
        FeatureDisableResponse resp = plugin.getFeatureLoadManager().disableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.alsoDisabledDependents().isEmpty()) {
                    send("command.disable.success_with_dependents", sender, Map.of(
                            "feature", feature,
                            "dependents", String.join(", ", resp.alsoDisabledDependents())
                    ));
                } else {
                    send("command.disable.success", sender, Map.of("feature", feature));
                }
            }
            case NOT_LOADED -> send("command.disable.not_loaded", sender, Map.of("feature", feature));
            default -> send("command.disable.failed", sender, Map.of("feature", feature));
        }
    }

    private void handleSoftReload(CommandSender sender, String feature) {
        FeatureSoftReloadResponse resp = plugin.getFeatureLoadManager().softReloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> send("command.softreload.success", sender, Map.of("feature", feature));
            case NOT_LOADED -> send("command.softreload.not_loaded", sender, Map.of("feature", feature));
            default -> send("command.softreload.failed", sender, Map.of("feature", feature));
        }
    }

    private void handleReload(CommandSender sender, String feature) {
        FeatureReloadResponse resp = plugin.getFeatureLoadManager().reloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.reloadedDependents().isEmpty()) {
                    send("command.reload.success_with_dependents", sender, Map.of(
                            "feature", feature,
                            "dependents", String.join(", ", resp.reloadedDependents())
                    ));
                } else {
                    send("command.reload.success", sender, Map.of("feature", feature));
                }
            }
            case NOT_LOADED -> send("command.reload.not_loaded", sender, Map.of("feature", feature));
            default -> send("command.reload.failed", sender, Map.of("feature", feature));
        }
    }

    private boolean has(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            send("general.no_permission", sender, Map.of());
            return false;
        }
        return true;
    }

    private void send(String key, CommandSender audience, Map<String, String> placeholders) {
        audience.sendMessage(plugin.getLocalizationHandler()
                .getMessage(key)
                .forAudience(audience)
                .withPlaceholders(placeholders)
                .build());
    }

    private void sendPluginStatus(@NotNull CommandSender sender) {
        List<BukkitBaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();
        List<String> loadedCommands = new ArrayList<>();
        int loadedFeatureCount = loadedFeatures.size();
        int activeTaskCount = 0;
        int registeredListenerCount = 0;
        int registeredCommandCount = 0;
        int activeConnCount = 0;

        for (BukkitBaseFeature<?> feature : loadedFeatures) {
            registeredCommandCount += feature.getLifecycleManager().getCommandManager().getRegisteredCommandCount();
            loadedCommands.addAll(feature.getLifecycleManager().getCommandManager().getRegisteredCommands().values().stream().map(Command::getName).toList());
            activeTaskCount += feature.getLifecycleManager().getTaskManager().getActiveTaskCount();
            registeredListenerCount += feature.getLifecycleManager().getListenerManager().getRegisteredListenerCount();
            activeConnCount += feature.getLifecycleManager().getDataManager().getActiveConnCount();
        }

        sender.sendMessage(Component.text("ServerFeatures Status:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- Number of loaded features: " + loadedFeatureCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active database connections: " + activeConnCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active tasks: " + activeTaskCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered listeners: " + registeredListenerCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered commands: " + registeredCommandCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Registered commands: " + loadedCommands, NamedTextColor.WHITE));
    }

    private void listLoadedFeatures(CommandSender sender) {
        List<BukkitBaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();

        if (loadedFeatures.isEmpty()) {
            send("command.list.empty", sender, Map.of());
            return;
        }

        send("command.list.header", sender, Map.of());
        for (BukkitBaseFeature<?> feature : loadedFeatures) {
            send("command.list.entry", sender, Map.of(
                    "feature", feature.getFeatureName(),
                    "version", feature.getFeatureVersion()
            ));
        }
    }
}
