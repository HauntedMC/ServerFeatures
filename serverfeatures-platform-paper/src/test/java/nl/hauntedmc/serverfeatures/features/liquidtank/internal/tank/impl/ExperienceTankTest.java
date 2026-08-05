package nl.hauntedmc.serverfeatures.features.liquidtank.internal.tank.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceTankTest {

    @Test
    void bottleWithdrawalRequiresSevenExperience() {
        assertEquals(-1, ExperienceTank.remainingAfterBottleWithdrawal(0));
        assertEquals(-1, ExperienceTank.remainingAfterBottleWithdrawal(6));
    }

    @Test
    void bottleWithdrawalPreservesAnyRemainder() {
        assertEquals(0, ExperienceTank.remainingAfterBottleWithdrawal(7));
        assertEquals(1, ExperienceTank.remainingAfterBottleWithdrawal(8));
        assertEquals(6, ExperienceTank.remainingAfterBottleWithdrawal(13));
        assertEquals(7, ExperienceTank.remainingAfterBottleWithdrawal(14));
    }
}
