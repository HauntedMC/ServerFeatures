package nl.hauntedmc.serverfeatures.features.lottery.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void keepsExactTwoDecimalMoney() {
        assertEquals("12.30", Money.parse("12.3").plain());
        assertEquals("15.00", Money.parse("10.00").add(Money.parse("5.00")).plain());
    }

    @Test
    void rejectsExcessPrecision() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse("1.001"));
    }
}
