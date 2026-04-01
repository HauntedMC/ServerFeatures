package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TankTypeTest {

    @Test
    void resolvesByNameCaseAndUnderscoreInsensitive() {
        assertEquals(TankType.BEETROOT_SOUP, TankType.getTankType("beetroot_soup"));
        assertEquals(TankType.BEETROOT_SOUP, TankType.getTankType("BeetrootSoup"));
        assertEquals(TankType.BEETROOT_SOUP, TankType.getTankType("BEETROOTSOUP"));
    }

    @Test
    void unknownTypeFallsBackToEmpty() {
        assertEquals(TankType.EMPTY, TankType.getTankType("not-a-type"));
    }
}

