package nl.hauntedmc.serverfeatures.api.util.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordUtilsTest {

    @Test
    void sendPayloadWritesExpectedRequestData() {
        FakeConnection connection = new FakeConnection(204);

        DiscordUtils.sendPayload("https://discord.test/webhook", "{\"content\":\"hello\"}", url -> connection);

        assertEquals("POST", connection.requestMethod);
        assertEquals("application/json", connection.headers.get("Content-Type"));
        assertTrue(connection.doOutput);
        assertEquals("{\"content\":\"hello\"}", connection.writtenBody());
        assertTrue(connection.disconnected);
    }

    @Test
    void sendPayloadSwallowsProviderFailures() {
        assertDoesNotThrow(() -> DiscordUtils.sendPayload(
                "https://discord.test/webhook",
                "{\"content\":\"x\"}",
                url -> {
                    throw new IllegalStateException("boom");
                }
        ));
    }

    private static final class FakeConnection extends HttpURLConnection {

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final int responseCode;
        private final Map<String, String> headers = new HashMap<>();
        private String requestMethod;
        private boolean doOutput;
        private boolean disconnected;

        private FakeConnection(int responseCode) {
            super(null);
            this.responseCode = responseCode;
        }

        @Override
        public void setRequestMethod(String method) {
            this.requestMethod = method;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public void setDoOutput(boolean doOutput) {
            this.doOutput = doOutput;
        }

        @Override
        public OutputStream getOutputStream() {
            return body;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        private String writtenBody() {
            return body.toString(StandardCharsets.UTF_8);
        }
    }
}
