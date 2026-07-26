package nl.hauntedmc.serverfeatures.api.io.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageTest {

    @Test
    void getFileNameMatchesExpectedConvention() {
        assertEquals("messages_NL.yml", Language.NL.getFileName());
        assertEquals("messages_EN.yml", Language.EN.getFileName());
    }
}
