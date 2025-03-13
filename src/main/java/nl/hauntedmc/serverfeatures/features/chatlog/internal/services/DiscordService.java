package nl.hauntedmc.serverfeatures.features.chatlog.internal.services;

import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;

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
    private final ChatLog chatLog;

    public DiscordService(ChatLog chatLog) {
        this.chatLog = chatLog;
    }

    /**
     * Sends a notification to the Discord staff channel via webhook as an embed.
     *
     * @param creator         The name or UUID of the player who created the report.
     * @param reportedPlayers A list of reported players (names or UUIDs).
     * @param server          The server name.
     * @param chatlogLink     The link to the chatlog report.
     */
    public void sendNotification(String creator, List<String> reportedPlayers, String server, String chatlogLink) {
        String webhookUrl = (String) chatLog.getConfigHandler().getSetting("discordWebhookURL");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            chatLog.getPlugin().getLogger().warning("Discord webhook URL not configured.");
            return;
        }

        // Build a current timestamp in ISO-8601 format.
        String timestamp = Instant.now().toString();

        // Build the embed payload with additional fields for a professional look.
        String payload = "{"
                + "\"embeds\": [{"
                + "    \"title\": \"Nieuwe Chatreport\","
                + "    \"url\": \"" + escapeJson(chatlogLink) + "\","
                + "    \"description\": \"Er is een nieuwe chatreport aangemaakt.\","
                + "    \"color\": 16776960," // Yellow color.
                + "    \"author\": {"
                + "         \"name\": \"HauntedMC\","
                + "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\""
                + "    },"
                + "    \"fields\": ["
                + "         {\"name\": \"Gemaakt door\", \"value\": \"" + escapeJson(creator) + "\", \"inline\": true},"
                + "         {\"name\": \"Reported speler(s)\", \"value\": \"" + escapeJson(String.join(", ", reportedPlayers)) + "\", \"inline\": true},"
                + "         {\"name\": \"Server\", \"value\": \"" + escapeJson(server) + "\", \"inline\": true},"
                + "         {\"name\": \"Chatreport link\", \"value\": \"" + escapeJson(chatlogLink) + "\"}"
                + "    ],"
                + "    \"timestamp\": \"" + timestamp + "\","
                + "    \"footer\": {"
                + "         \"text\": \"HauntedMC ChatLog " + chatLog.getFeatureVersion() + " \","
                + "         \"icon_url\": \"https://hauntedmc.nl/HauntedLog.png\""
                + "    }"
                + "}]"
                + "}";

        try {
            URL url = URI.create(webhookUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();

            chatLog.getPlugin().getLogger().info("Discord webhook response: " + responseCode + " " + responseMessage);

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    chatLog.getPlugin().getLogger().warning("Discord webhook error response: " + errorResponse.toString());
                } catch (Exception ex) {
                    chatLog.getPlugin().getLogger().warning("Failed to read error response from Discord webhook: " + ex.getMessage());
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            chatLog.getPlugin().getLogger().warning("Failed to send Discord notification: " + e.getMessage());
        }
    }

    /**
     * Escapes special characters in a string for JSON payloads.
     *
     * @param str the input string.
     * @return the escaped string.
     */
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
