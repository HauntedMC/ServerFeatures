package nl.hauntedmc.serverfeatures.features.chatfilter.internal.services;

import nl.hauntedmc.serverfeatures.common.util.DiscordUtils;
import nl.hauntedmc.serverfeatures.common.util.JsonUtils;
import nl.hauntedmc.serverfeatures.features.chatfilter.ChatFilter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

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
        String server = (String) feature.getConfigHandler().getSetting("server");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            feature.getPlugin().getLogger().warning("Discord webhook URL not configured.");
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
        DiscordUtils.sendPayload(webhookUrl, payload, feature);
    }
}
