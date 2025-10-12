package nl.hauntedmc.serverfeatures.features.chatfilter.internal.services;

import nl.hauntedmc.serverfeatures.api.util.http.DiscordUtils;
import nl.hauntedmc.serverfeatures.api.util.parse.JsonUtils;
import nl.hauntedmc.serverfeatures.features.chatfilter.ChatFilter;
import java.time.Instant;

public class DiscordService {
    private final ChatFilter feature;

    public DiscordService(ChatFilter feature) {
        this.feature = feature;
    }

    /**
     * Sends a notification for chat filter events as an embed with red color.
     *
     * @param playerName    The name of the player whose message was filtered.
     * @param filterType    The type of filter triggered (e.g. "Blocked Link", "Blocked IP", "Blocked Word", etc.).
     * @param filteredMsg   The filtered message.
     */
    public void sendFilterNotification(String playerName, String filterType, String filteredMsg) {
        String webhookUrl = (String) feature.getConfigHandler().getSetting("discordWebhookURL");
        String server = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            feature.getLogger().warning("Discord webhook URL not configured.");
            return;
        }

        String timestamp = Instant.now().toString();
        String payload = "{"
                + "\"embeds\": [{"
                + "    \"title\": \"Chatfilter Melding\","
                + "    \"description\": \"Er is een bericht gemarkeerd door het chatfilter.\","
                + "    \"color\": 16711680,"  // Red color.
                + "    \"author\": {"
                + "         \"name\": \"HauntedMC\","
                + "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\""
                + "    },"
                + "    \"fields\": ["
                + "         {\"name\": \"Speler\", \"value\": \"" + JsonUtils.escapeJson(playerName) + "\", \"inline\": true},"
                + "         {\"name\": \"Server\", \"value\": \"" + JsonUtils.escapeJson(server) + "\", \"inline\": true},"
                + "         {\"name\": \"Filter Type\", \"value\": \"" + JsonUtils.escapeJson(filterType) + "\", \"inline\": true},"
                + "         {\"name\": \"Bericht\", \"value\": \"" + JsonUtils.escapeJson(filteredMsg) + "\"}"
                + "    ],"
                + "    \"timestamp\": \"" + timestamp + "\","
                + "    \"footer\": {"
                + "         \"text\": \"HauntedMC ChatFilter " + feature.getFeatureVersion() + "\","
                + "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\""
                + "    }"
                + "}]"
                + "}";
        DiscordUtils.sendPayload(webhookUrl, payload);
    }
}
