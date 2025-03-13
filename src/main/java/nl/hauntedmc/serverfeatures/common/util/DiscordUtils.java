package nl.hauntedmc.serverfeatures.common.util;

import nl.hauntedmc.serverfeatures.features.BaseFeature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordUtils {


    /**
     * Sends the provided JSON payload to the specified Discord webhook URL.
     *
     * @param webhookUrl The webhook URL.
     * @param payload    The JSON payload.
     */
    public static void sendPayload(String webhookUrl, String payload, BaseFeature<?> feature) {
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

            feature.getPlugin().getLogger().info("Discord webhook response: " + responseCode + " " + responseMessage);

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    feature.getPlugin().getLogger().warning("Discord webhook error response: " + errorResponse.toString());
                } catch (Exception ex) {
                    feature.getPlugin().getLogger().warning("Failed to read error response from Discord webhook: " + ex.getMessage());
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            feature.getPlugin().getLogger().warning("Failed to send Discord notification: " + e.getMessage());
        }
    }


}
