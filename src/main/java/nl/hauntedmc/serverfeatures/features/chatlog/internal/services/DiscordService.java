package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.serverfeatures.common.util.DiscordUtils;
import nl.hauntedmc.serverfeatures.common.util.JsonUtils;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;

import java.time.Instant;
import java.util.List;

public class DiscordService {
    private final ChatLog feature;

    public DiscordService(ChatLog feature) {
        this.feature = feature;
    }

    /**
     * Sends a notification to the Discord staff channel via webhook as an embed.
     * (For chat report events.)
     *
     * @param creator         The name or UUID of the player who created the report.
     * @param reportedPlayers A list of reported players (names or UUIDs).
     * @param server          The server name.
     * @param chatlogLink     The link to the chatlog report.
     */
    public void sendNotification(String creator, List<String> reportedPlayers, String server, String chatlogLink) {
        String webhookUrl = (String) feature.getConfigHandler().getSetting("discordWebhookURL");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            feature.getPlugin().getLogger().warning("Discord webhook URL not configured.");
            return;
        }

        // Build a current timestamp in ISO-8601 format.
        String timestamp = Instant.now().toString();

        // Build the embed payload with yellow color (for chat reports).
        String payload = "{"
                + "\"embeds\": [{"
                + "    \"title\": \"Nieuwe Chatreport\","
                + "    \"url\": \"" + JsonUtils.escapeJson(chatlogLink) + "\","
                + "    \"description\": \"Er is een nieuwe chatreport aangemaakt.\","
                + "    \"color\": 16776960,"  // Yellow color.
                + "    \"author\": {"
                + "         \"name\": \"HauntedMC\","
                + "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\""
                + "    },"
                + "    \"fields\": ["
                + "         {\"name\": \"Gemaakt door\", \"value\": \"" + JsonUtils.escapeJson(creator) + "\", \"inline\": true},"
                + "         {\"name\": \"Reported speler(s)\", \"value\": \"" + JsonUtils.escapeJson(String.join(", ", reportedPlayers)) + "\", \"inline\": true},"
                + "         {\"name\": \"Server\", \"value\": \"" + JsonUtils.escapeJson(server) + "\", \"inline\": true},"
                + "         {\"name\": \"Chatreport link\", \"value\": \"" + JsonUtils.escapeJson(chatlogLink) + "\"}"
                + "    ],"
                + "    \"timestamp\": \"" + timestamp + "\","
                + "    \"footer\": {"
                + "         \"text\": \"HauntedMC ChatLog " + feature.getFeatureVersion() + "\","
                + "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\""
                + "    }"
                + "}]"
                + "}";
        DiscordUtils.sendPayload(webhookUrl, payload, feature);
    }
}
