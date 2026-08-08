package nl.hauntedmc.serverfeatures.toolkit.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonStringsTest {
    @Test
    void escapeJsonEscapesBackslashQuotesAndNewlines() {
        assertEquals("a\\\\b\\\"c\\nd", JsonStrings.escapeJson("a\\b\"c\nd"));
    }
}
