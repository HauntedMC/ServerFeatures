package nl.hauntedmc.serverfeatures.api.util.text.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormatterTest {

    @Test
    void toMiniMessageConvertsLegacyCodesAndHex() {
        String mm = TextFormatter.convert("&aHello §bWorld &#ff00ff!")
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .toMiniMessage();

        assertTrue(mm.contains("<green>Hello"));
        assertTrue(mm.contains("<aqua>World"));
        assertTrue(mm.contains("<#ff00ff>!"));
    }

    @Test
    void toLegacySupportsHexOutputModes() {
        String defaultHex = TextFormatter.convert("<#00ff00>G")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .toLegacy('&');
        String xRepeated = TextFormatter.convert("<#abcdef>X")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .options(o -> o.xRepeatedHex(true))
                .toLegacy('&');
        String downsampled = TextFormatter.convert("<#ffffff>W")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .options(o -> o.legacyOutputHexColors(false))
                .toLegacy('&');

        assertTrue(defaultHex.contains("&#00FF00"));
        assertTrue(xRepeated.contains("&x&A&B&C&D&E&F"));
        assertTrue(downsampled.contains("&fW"));
    }

    @Test
    void toLegacyEmitsResetForClosingTagsByDefault() {
        String legacy = TextFormatter.convert("<red>R</red>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .toLegacy('&');

        assertEquals("&cR&r", legacy);
    }

    @Test
    void toPlainAndStripUtilitiesRemoveFormatting() {
        assertEquals("Hi there", TextFormatter.toPlain("&aHi <bold>there</bold>"));
        assertEquals("A B C", TextFormatter.stripLegacyCodes("&aA §bB &#aabbccC"));
        assertEquals("ABC", TextFormatter.stripMiniMessageTags("<green>A<#aabbcc>B</green>C"));
    }

    @Test
    void escapeForMiniMessageEscapesTags() {
        String escaped = TextFormatter.escapeForMiniMessage("<red>hello</red>");
        assertTrue(escaped.contains("\\<red>"));
    }
}
