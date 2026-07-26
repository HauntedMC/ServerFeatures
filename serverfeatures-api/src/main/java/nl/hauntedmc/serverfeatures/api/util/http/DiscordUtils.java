package nl.hauntedmc.serverfeatures.api.util.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordUtils {

    @FunctionalInterface
    interface ConnectionProvider {
        HttpURLConnection open(URL url) throws Exception;
    }

    /**
     * Sends the provided JSON payload to the specified Discord webhook URL.
     *
     * @param webhookUrl The webhook URL.
     * @param payload    The JSON payload.
     */
    public static void sendPayload(String webhookUrl, String payload) {
        sendPayload(webhookUrl, payload, url -> (HttpURLConnection) url.openConnection());
    }

    static void sendPayload(String webhookUrl, String payload, ConnectionProvider connectionProvider) {
        try {
            URL url = URI.create(webhookUrl).toURL();
            HttpURLConnection connection = connectionProvider.open(url);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        errorResponse.append(line);
                    }
                } catch (Exception ignored) {
                }
            }
            connection.disconnect();
        } catch (Exception ignored) {
        }
    }


}
