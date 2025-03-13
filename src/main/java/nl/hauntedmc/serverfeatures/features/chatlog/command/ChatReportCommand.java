package nl.hauntedmc.serverfeatures.features.chatlog.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import nl.hauntedmc.serverfeatures.commands.FeatureCommand;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ChatReportCommand extends FeatureCommand {
    private final ChatLog feature;

    public ChatReportCommand(ChatLog feature) {
        super("chatreport");
        this.feature = feature;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("serverfeatures.feature.chatlog.use")) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission", sender));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command", sender));
            return true;
        }

        // Display usage/help if no arguments are provided.
        if (args.length < 1) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("chatlog.help", sender));
            return true;
        }

        // Determine the report timeframe from configuration.
        long currentTime = System.currentTimeMillis();
        int timeframeMinutes = (int) feature.getConfigHandler().getSetting("reportTimeFrameMinutes");
        long reportStart = currentTime - timeframeMinutes * 60 * 1000L;
        String serverName = (String) feature.getConfigHandler().getSetting("server");

        // Check each provided player UUID argument.
        List<String> reportedPlayers = new ArrayList<>();
        for (String playerName : args) {
            if (!reportedPlayers.contains(playerName)) {
                int count = feature.getReportHandler().checkMessage(serverName, playerName, reportStart, currentTime);
                if (count >= 1) {
                    reportedPlayers.add(playerName);
                } else {
                    player.sendMessage(feature.getLocalizationHandler().getMessage("chatlog.error", sender, Map.of("name", playerName)));
                }
            }
        }

        // Create a report if valid messages exist.
        if (!reportedPlayers.isEmpty()) {
            String reportId = UUID.randomUUID().toString().replace("-", "");
            feature.getReportHandler().createReport(serverName, reportedPlayers, reportStart, currentTime, reportId);
            String baseUrl = (String) feature.getConfigHandler().getSetting("URL");
            String fullUrl = baseUrl + reportId;

            // Build a clickable component with the report URL.
            Component clickableUrl = feature.getLocalizationHandler().getMessage("chatlog.url", sender, Map.of("url", fullUrl))
                    .clickEvent(ClickEvent.openUrl(fullUrl));
            player.sendMessage(clickableUrl);
            feature.getReportHandler().sendDiscordNotifaction(player.getName(), reportedPlayers, serverName, fullUrl);
        } else {
            player.sendMessage(feature.getLocalizationHandler().getMessage("chatlog.errorNotSaved", sender));
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        String partial = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        List<String> completions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase().startsWith(partial)) {
                completions.add(name);
            }
        }
        return completions;
    }
}
