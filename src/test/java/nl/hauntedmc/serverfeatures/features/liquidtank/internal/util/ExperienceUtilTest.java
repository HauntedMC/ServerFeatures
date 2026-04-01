package nl.hauntedmc.serverfeatures.features.liquidtank.internal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceUtilTest {

    @Test
    void expConversionHelpersReturnExpectedValuesAtBoundaries() {
        assertEquals(0, ExperienceUtil.lvlToExp(0));
        assertEquals(112, ExperienceUtil.lvlToExp(8));
        assertEquals(315, ExperienceUtil.lvlToExp(15));
        assertEquals(394, ExperienceUtil.lvlToExp(17));
        assertEquals(1395, ExperienceUtil.lvlToExp(30));
        assertEquals(1507, ExperienceUtil.lvlToExp(31));
    }

    @Test
    void expToLevelUpMatchesPiecewiseFormula() {
        assertEquals(7, ExperienceUtil.expToLvlUp(0));
        assertEquals(39, ExperienceUtil.expToLvlUp(16));
        assertEquals(47, ExperienceUtil.expToLvlUp(17));
        assertEquals(117, ExperienceUtil.expToLvlUp(31));
        assertEquals(130, ExperienceUtil.expToLvlUp(32));
    }

    @Test
    void getLevelFindsHighestLevelForTotalExpUpToThirty() {
        assertEquals(0, ExperienceUtil.getLevel(0));
        assertEquals(1, ExperienceUtil.getLevel(7));
        assertEquals(16, ExperienceUtil.getLevel(352));
        assertEquals(17, ExperienceUtil.getLevel(394));
        assertEquals(30, ExperienceUtil.getLevel(1395));
    }
}

