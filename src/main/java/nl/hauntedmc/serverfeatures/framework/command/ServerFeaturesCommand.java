package nl.hauntedmc.serverfeatures.framework.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.serverfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.serverfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.serverfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class ServerFeaturesCommand implements CommandExecutor, TabCompleter {

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("general.usage")
                    .forAudience(sender)
                    .build());
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
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.softreload.usage")
                            .forAudience(sender)
                            .build());
                    return true;
                }
                handleSoftReload(sender, args[1]);
                return true;

            case "reload":
                if (!has(sender, "serverfeatures.command.reload")) return true;
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.usage")
                            .forAudience(sender)
                            .build());
                    return true;
                }
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
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reloadlocal.success")
                            .forAudience(sender)
                            .build());
                } catch (Throwable t) {
                    plugin.getLogger().warning("Localization reload failed: " + t.getMessage());
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reloadlocal.fail")
                            .forAudience(sender)
                            .build());
                }
                return true;

            default:
                sender.sendMessage(plugin.getLocalizationHandler()
                        .getMessage("general.unknown_command")
                        .forAudience(sender)
                        .build());
                return true;
        }
    }

    private void handleEnable(CommandSender sender, String feature) {
        FeatureEnableResponse resp = plugin.getFeatureLoadManager().enableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.success")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            case NOT_FOUND -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.not_found")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            case ALREADY_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.already_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            case MISSING_PLUGIN_DEPENDENCY -> {
                String plugins = String.join(", ", resp.missingPlugins());
                sender.sendMessage(plugin.getLocalizationHandler()
                        .getMessage("command.enable.missing_plugin_dependency")
                        .forAudience(sender)
                        .with("feature", feature)
                        .with("plugins", plugins)
                        .build());
            }

            case MISSING_FEATURE_DEPENDENCY -> {
                String deps = String.join(", ", resp.missingFeatures());
                sender.sendMessage(plugin.getLocalizationHandler()
                        .getMessage("command.enable.missing_feature_dependency")
                        .forAudience(sender)
                        .with("feature", feature)
                        .with("features", deps)
                        .build());
            }

            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private void handleDisable(CommandSender sender, String feature) {
        FeatureDisableResponse resp = plugin.getFeatureLoadManager().disableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.alsoDisabledDependents().isEmpty()) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.disable.success_with_dependents")
                            .forAudience(sender)
                            .with("feature", feature)
                            .with("dependents", String.join(", ", resp.alsoDisabledDependents()))
                            .build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.disable.success")
                            .forAudience(sender)
                            .with("feature", feature)
                            .build());
                }
            }

            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.disable.not_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.disable.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private void handleSoftReload(CommandSender sender, String feature) {
        FeatureSoftReloadResponse resp = plugin.getFeatureLoadManager().softReloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.success")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.not_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private void handleReload(CommandSender sender, String feature) {
        FeatureReloadResponse resp = plugin.getFeatureLoadManager().reloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.reloadedDependents().isEmpty()) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.success_with_dependents")
                            .forAudience(sender)
                            .with("feature", feature)
                            .with("dependents", String.join(", ", resp.reloadedDependents()))
                            .build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.success")
                            .forAudience(sender)
                            .with("feature", feature)
                            .build());
                }
            }

            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.reload.not_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());

            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.reload.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private boolean has(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return false;
        }
        return true;
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
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.list.empty")
                    .forAudience(sender)
                    .build());
            return;
        }

        sender.sendMessage(plugin.getLocalizationHandler()
                .getMessage("command.list.header")
                .forAudience(sender)
                .build());

        for (BukkitBaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.list.entry")
                    .forAudience(sender)
                    .with("feature", feature.getFeatureName())
                    .with("version", feature.getFeatureVersion())
                    .build());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        final Locale L = Locale.ROOT;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(L);
            return Stream.of("list", "disable", "enable", "reload", "reloadlocal", "softreload", "status")
                    .filter(s -> s.toLowerCase(L).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(L);
            String featurePrefix = args[1].toLowerCase(L);

            return switch (sub) {
                case "reload", "softreload", "disable" ->
                        plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                                .map(BukkitBaseFeature::getFeatureName)
                                .filter(name -> name != null && name.toLowerCase(L).startsWith(featurePrefix))
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .toList();

                case "enable" -> {
                    Set<String> loadedLower = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                            .map(BukkitBaseFeature::getFeatureName)
                            .filter(Objects::nonNull)
                            .map(s -> s.toLowerCase(L))
                            .collect(java.util.stream.Collectors.toSet());

                    yield plugin.getFeatureLoadManager().getFeatureRegistry().getAvailableFeatures().keySet().stream()
                            .filter(Objects::nonNull)
                            .filter(name -> !loadedLower.contains(name.toLowerCase(L)))
                            .filter(name -> name.toLowerCase(L).startsWith(featurePrefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                }
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }
}
