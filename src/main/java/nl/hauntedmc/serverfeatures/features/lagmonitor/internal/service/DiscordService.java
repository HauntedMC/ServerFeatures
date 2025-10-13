package nl.hauntedmc.serverfeatures.features.lagmonitor.internal.service;

import nl.hauntedmc.serverfeatures.api.util.http.DiscordUtils;
import nl.hauntedmc.serverfeatures.api.util.parse.JsonUtils;
import nl.hauntedmc.serverfeatures.features.lagmonitor.LagMonitor;

import java.time.Instant;

public class DiscordService {
    private final LagMonitor feature;

    public DiscordService(LagMonitor feature) {
        this.feature = feature;
    }

    /**
     * Sends a TPS warning notification to Discord as an embed with orange color.
     *
     * @param avgTPS The average TPS recorded.
     */
    public void sendNotification(String avgTPS) {
        String serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
        String webhookUrl = (String) feature.getConfigHandler().getSetting("discordWebhookURL");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            feature.getLogger().warning("Discord webhook URL not configured.");
            return;
        }

        String timestamp = Instant.now().toString();
        String payload = "{" +
                "\"embeds\": [{" +
                "    \"title\": \"⚠️ Server Lag Waarschuwing\"," +
                "    \"description\": \"Er is een lage server TPS gedetecteerd.\"," +
                "    \"color\": 16753920," +  // Orange color.
                "    \"author\": {" +
                "         \"name\": \"HauntedMC\"," +
                "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\"" +
                "    }," +
                "    \"fields\": [" +
                "         {\"name\": \"Server\", \"value\": \"" + JsonUtils.escapeJson(serverName) + "\", \"inline\": true}," +
                "         {\"name\": \"Gemiddelde TPS\", \"value\": \"" + avgTPS + "\", \"inline\": true}" +
                "    ]," +
                "    \"timestamp\": \"" + timestamp + "\"," +
                "    \"footer\": {" +
                "         \"text\": \"HauntedMC LagMonitor " + feature.getFeatureVersion() + "\"," +
                "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\"" +
                "    }" +
                "}]" +
                "}";
        DiscordUtils.sendPayload(webhookUrl, payload);
    }
}
