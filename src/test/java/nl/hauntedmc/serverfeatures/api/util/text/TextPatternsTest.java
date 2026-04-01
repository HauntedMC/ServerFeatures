package nl.hauntedmc.serverfeatures.api.util.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TextPatternsTest {

    @Test
    void keyPatternsMatchExpectedInputs() {
        assertTrue(TextPatterns.AMP_CODES.matcher("&aHello").find());
        assertTrue(TextPatterns.SEC_CODES.matcher("§bHello").find());
        assertTrue(TextPatterns.POUND_HEX.matcher("&#a1b2c3").find());
        assertTrue(TextPatterns.AMP_BUNGEE_HEX.matcher("&x&a&b&c&d&e&f").find());
        assertTrue(TextPatterns.MINI_HEX_TAG.matcher("<#aabbcc>").find());
        assertTrue(TextPatterns.ANY_MINI_TAG.matcher("<bold>x</bold>").find());
        assertTrue(TextPatterns.URL.matcher("visit https://example.com now").find());
        assertTrue(TextPatterns.MC_NAME.matcher("Player_123").matches());
        assertTrue(TextPatterns.BUKKIT_ALIAS_FORMAT.matcher("alias_1").matches());
        assertTrue(TextPatterns.MC_IN_VERSION.matcher("(MC: 1.21.1)").find());
        assertTrue(TextPatterns.DATE_IN_NAME.matcher("backup_01-12-2025.zip").matches());
    }
}
