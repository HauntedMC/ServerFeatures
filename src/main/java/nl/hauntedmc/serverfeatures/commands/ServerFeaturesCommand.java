package nl.hauntedmc.serverfeatures.commands;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import org.bukkit.command.*;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ServerFeaturesCommand implements CommandExecutor, TabCompleter {

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /serverfeatures <subcommand>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                if (!sender.hasPermission("serverfeatures.command.list")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to reload all features.");
                    return true;
                }
                listLoadedFeatures(sender);
                return true;

            case "reload":
                if (!sender.hasPermission("serverfeatures.command.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to reload features.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /serverfeatures reload <feature>");
                    return true;
                }
                if (plugin.getFeatureLoadManager().reloadFeature(args[1])) {
                    sender.sendMessage(ChatColor.GREEN + "Feature " + args[1] + " reloaded.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Feature not found or not enabled.");
                }
                return true;

            case "enable":
                if (!sender.hasPermission("serverfeatures.command.enable")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to enable features.");
                    return true;
                }
                if (args.length < 2) return false;
                if (plugin.getFeatureLoadManager().enableFeature(args[1])) {
                    sender.sendMessage(ChatColor.GREEN + "Feature " + args[1] + " enabled.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Feature not found or already enabled.");
                }
                return true;

            case "disable":
                if (!sender.hasPermission("serverfeatures.command.disable")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to disable features.");
                    return true;
                }
                if (args.length < 2) return false;
                if (plugin.getFeatureLoadManager().disableFeature(args[1])) {
                    sender.sendMessage(ChatColor.YELLOW + "Feature " + args[1] + " disabled.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Feature not found or already disabled.");
                }
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown command.");
                return true;
        }
    }


    private void listLoadedFeatures(CommandSender sender) {
        List<BaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();

        if (loadedFeatures.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No features are currently loaded.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Loaded Features:");
        for (BaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(ChatColor.GOLD + " - " + feature.getFeatureName() + " (v" + feature.getFeatureVersion() + ")");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("reload");
            completions.add("enable");
            completions.add("disable");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "reload":
                case "disable":
                    // Suggest only currently loaded features
                    completions.addAll(plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                            .map(BaseFeature::getFeatureName)
                            .toList());
                    break;

                case "enable":
                    // Suggest only available features that are NOT loaded
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
