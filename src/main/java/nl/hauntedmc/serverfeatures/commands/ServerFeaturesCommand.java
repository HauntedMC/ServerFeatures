package nl.hauntedmc.serverfeatures.commands;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
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
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                if (!sender.hasPermission("serverfeatures.command.list")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                listLoadedFeatures(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("serverfeatures.command.reload")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.usage"));
                    return true;
                }
                if (plugin.getFeatureLoadManager().reloadFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.success", Map.of("feature", args[1])));
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.fail"));
                }
                return true;

            case "enable":
                if (!sender.hasPermission("serverfeatures.command.enable")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 2) return false;
                if (plugin.getFeatureLoadManager().enableFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.enable.success", Map.of("feature", args[1])));
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.enable.fail"));
                }
                return true;

            case "disable":
                if (!sender.hasPermission("serverfeatures.command.disable")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 2) return false;
                if (plugin.getFeatureLoadManager().disableFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.disable.success", Map.of("feature", args[1])));
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.disable.fail"));
                }
                return true;

            case "reloadlocal":
                if (!sender.hasPermission("serverfeatures.command.reloadlocal")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return true;
                }
                plugin.getLocalizationHandler().reloadLocalization();
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reloadlocal.success"));
                return true;

            default:
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.unknown_command"));
                return true;
        }
    }

    private void listLoadedFeatures(CommandSender sender) {
        List<BaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();

        if (loadedFeatures.isEmpty()) {
            sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.list.empty"));
            return;
        }

        sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.list.header"));
        for (BaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(plugin.getLocalizationHandler().getMessage(
                    "command.list.entry",
                    Map.of("feature", feature.getFeatureName(), "version", feature.getFeatureVersion())
            ));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("reload");
            completions.add("reloadlocal");
            completions.add("enable");
            completions.add("disable");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "reload":
                case "disable":
                    completions.addAll(plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                            .map(BaseFeature::getFeatureName)
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
