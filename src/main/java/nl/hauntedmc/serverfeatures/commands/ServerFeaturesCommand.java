package nl.hauntedmc.serverfeatures.commands;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.List;

public class ServerFeaturesCommand implements CommandExecutor {

    private final ServerFeatures plugin;

    public ServerFeaturesCommand(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverfeatures.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /serverfeatures list");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listLoadedFeatures(sender);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /serverfeatures list");
        return true;
    }

    private void listLoadedFeatures(CommandSender sender) {
        List<BaseFeature<?>> loadedFeatures = plugin.getLoadedFeatures();

        if (loadedFeatures.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No features are currently loaded.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Loaded Features:");
        for (BaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(ChatColor.GOLD + " - " + feature.getFeatureName() + " (v" + feature.getFeatureVersion() + ")");
        }
    }
}
