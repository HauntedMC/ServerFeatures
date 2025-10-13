package nl.hauntedmc.serverfeatures.features.chatlog.internal;

import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import nl.hauntedmc.serverfeatures.features.chatlog.internal.services.ChatLogService;
import nl.hauntedmc.serverfeatures.features.chatlog.internal.services.DiscordService;
import org.bukkit.entity.Player;

import java.util.List;

public class ReportHandler {

    private final ChatLogService chatLogService;
    private final DiscordService discordService;
    private final ChatLog feature;

    public ReportHandler(ChatLog feature) {
        this.feature = feature;
        this.chatLogService = new ChatLogService(feature);
        this.discordService = new DiscordService(feature);
    }

    /**
     * Called when a player sends a chat message.
     */
    public void logMessage(Player player, String rawMessage) {
        chatLogService.addMessage(player, rawMessage);
    }

    /**
     * Checks how many messages a given player (by UUID) has sent between two timestamps.
     */
    public int checkMessage(String server, String playerName, Long start, Long end) {
        return chatLogService.countMessages(server, playerName, start, end);
    }

    /**
     * Creates a report by collecting messages from a list of players.
     */
    public void createReport(String server, List<String> players, Long start, Long end, String reportId) {
        chatLogService.createReport(server, players, start, end, reportId);
    }

    public void sendDiscordNotifaction(String creator, List<String> reportedPlayers, String serverName, String chatlogLink) {
        feature.getLifecycleManager().getTaskManager().scheduleAsyncTask(() -> discordService.sendNotification(creator, reportedPlayers, serverName, chatlogLink));
    }
}
