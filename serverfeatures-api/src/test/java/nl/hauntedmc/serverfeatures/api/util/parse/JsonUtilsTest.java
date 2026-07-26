package nl.hauntedmc.serverfeatures.api.util.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonUtilsTest {

    @Test
    void escapeJsonEscapesBackslashQuoteAndNewline() {
        String raw = "a\\b\"c\nd";
        assertEquals("a\\\\b\\\"c\\nd", JsonUtils.escapeJson(raw));
    }

    @Test
    void escapeJsonLeavesRegularTextUnchanged() {
        assertEquals("plain-text", JsonUtils.escapeJson("plain-text"));
        assertEquals("", JsonUtils.escapeJson(""));
    }

    @Test
    void escapeJsonDoesNotSilentlyAcceptNull() {
        assertThrows(NullPointerException.class, () -> JsonUtils.escapeJson(null));
    }
}
