package nl.hauntedmc.serverfeatures.api.util.text.format.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyColorUtilsTest {

    @Test
    void convertAmpCodesToMiniReplacesColorsAndFormats() {
        String converted = LegacyColorUtils.convertAmpCodesToMini("&aHello &lBold &rReset");
        assertTrue(converted.contains("<green>Hello"));
        assertTrue(converted.contains("<bold>Bold"));
        assertTrue(converted.contains("<reset>Reset"));
    }

    @Test
    void convertSectionCodesToMiniReplacesColorsAndFormats() {
        String converted = LegacyColorUtils.convertSecCodesToMini("§cRed §oItalic");
        assertTrue(converted.contains("<red>Red"));
        assertTrue(converted.contains("<italic>Italic"));
    }

    @Test
    void nearestNamedLegacyCodePicksClosestColor() {
        assertEquals('f', LegacyColorUtils.nearestNamedLegacyCode("FFFFFF"));
        assertEquals('0', LegacyColorUtils.nearestNamedLegacyCode("000000"));
    }

    @Test
    void tagMapContainsAliases() {
        assertEquals('7', LegacyColorUtils.TAG_TO_CODE.get("grey"));
        assertEquals('d', LegacyColorUtils.TAG_TO_CODE.get("pink"));
    }
}
