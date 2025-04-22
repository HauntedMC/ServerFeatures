package nl.hauntedmc.serverfeatures.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ServerFeaturesCommand implements CommandExecutor, TabCompleter {

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.usage").forAudience(sender).build());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                if (!sender.hasPermission("serverfeatures.command.status")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                sendPluginStatus(sender);
                return true;

            case "list":
                if (!sender.hasPermission("serverfeatures.command.list")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                listLoadedFeatures(sender);
                return true;

            case "softreload":
                if (!sender.hasPermission("serverfeatures.command.reload")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.softreload.usage").forAudience(sender).build());
                    return true;
                }
                if (plugin.getFeatureLoadManager().softReloadFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.softreload.success").forAudience(sender).withPlaceholders(Map.of("feature", args[1])).build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.softreload.fail").forAudience(sender).build());
                }
                return true;

            case "reload":
                if (!sender.hasPermission("serverfeatures.command.reload")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.usage").forAudience(sender).build());
                    return true;
                }
                if (plugin.getFeatureLoadManager().reloadFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.success").forAudience(sender).withPlaceholders(Map.of("feature", args[1])).build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.fail").forAudience(sender).build());
                }
                return true;

            case "enable":
                if (!sender.hasPermission("serverfeatures.command.enable")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) return false;
                if (plugin.getFeatureLoadManager().enableFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.enable.success").forAudience(sender).withPlaceholders(Map.of("feature", args[1])).build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.enable.fail").forAudience(sender).build());
                }
                return true;

            case "disable":
                if (!sender.hasPermission("serverfeatures.command.disable")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                if (args.length < 2) return false;
                if (plugin.getFeatureLoadManager().disableFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.disable.success").forAudience(sender).withPlaceholders( Map.of("feature", args[1])).build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.disable.fail").forAudience(sender).build());
                }
                return true;

            case "reloadlocal":
                if (!sender.hasPermission("serverfeatures.command.reloadlocal")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission").forAudience(sender).build());
                    return true;
                }
                plugin.getLocalizationHandler().reloadLocalization();
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reloadlocal.success").forAudience(sender).build());
                return true;

            default:
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.unknown_command").forAudience(sender).build());
                return true;
        }
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
            sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.list.empty").forAudience(sender).build());
            return;
        }

        sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.list.header").forAudience(sender).build());
        for (BukkitBaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(plugin.getLocalizationHandler().getMessage(
                    "command.list.entry").forAudience(sender).withPlaceholders(
                    Map.of("feature", feature.getFeatureName(), "version", feature.getFeatureVersion())).build()
            );
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("disable");
            completions.add("enable");
            completions.add("reload");
            completions.add("reloadlocal");
            completions.add("softreload");
            completions.add("status");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "reload":
                case "softreload":
                case "disable":
                    completions.addAll(plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                            .map(BukkitBaseFeature::getFeatureName)
                            .toList());
                    break;

                case "enable":
                    completions.addAll(plugin.getFeatureLoadManager().getFeatureRegistry().getAvailableFeatures().keySet().stream()
                            .filter(feature -> plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                                    .noneMatch(loadedFeature -> loadedFeature.getFeatureName().equalsIgnoreCase(feature)))
                            .toList());
                    break;
            }
        }
        return completions;
    }
}
