package nl.hauntedmc.serverfeatures.framework.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.Sources;
import nl.hauntedmc.serverfeatures.api.command.tab.suggestion.SuggestionSource;
import nl.hauntedmc.serverfeatures.api.command.tab.TabTree;
import nl.hauntedmc.serverfeatures.api.command.tab.types.ArgTypes;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.serverfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.serverfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.serverfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ServerFeaturesCommand implements CommandExecutor {

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    /** Build the TabTree so ServerFeatures can register it with the global TabService. */
    public TabTree createTabTree() {
        // Dynamic sources
        final SuggestionSource loadedFeatures = Sources.dynamic(ctx ->
                plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                        .map(BukkitBaseFeature::getFeatureName)
                        .filter(Objects::nonNull)
                        .toList()
        );

        final SuggestionSource availableButNotLoaded = Sources.dynamic(ctx -> {
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

        return TabTree.builder()
                .root()
                .literal("status", cfg -> cfg.require("serverfeatures.command.status"))
                .literal("list",   cfg -> cfg.require("serverfeatures.command.list"))
                .literal("reloadlocal", cfg -> cfg.require("serverfeatures.command.reloadlocal"))
                .literal("softreload", cfg -> cfg.require("serverfeatures.command.reload").child()
                        .arg("feature", ArgTypes.string(), loadedFeatures))
                .literal("reload", cfg -> cfg.require("serverfeatures.command.reload").child()
                        .arg("feature", ArgTypes.string(), loadedFeatures))
                .literal("disable", cfg -> cfg.require("serverfeatures.command.disable").child()
                        .arg("feature", ArgTypes.string(), loadedFeatures))
                .literal("enable", cfg -> cfg.require("serverfeatures.command.enable").child()
                        .arg("feature", ArgTypes.string(), availableButNotLoaded))
                .build();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            send("general.usage", sender, Map.of());
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
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
