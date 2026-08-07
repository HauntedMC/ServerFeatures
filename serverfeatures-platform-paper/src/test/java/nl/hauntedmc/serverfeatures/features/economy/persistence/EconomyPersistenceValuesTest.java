package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyPersistenceValuesTest {
    private static final EconomySettings.Currency MONEY = new EconomySettings.Currency(
            "money",
            new EconomyScope(EconomyScopeType.GLOBAL, "hauntedmc/global"),
            new EconomySettings.Display("coin", "coins", "$", "{amount}", 2, true),
            new EconomySettings.Balances(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), false,
                    RoundingMode.HALF_UP),
            new EconomySettings.Commands("money", List.of(), true, true, true, true, true, true),
            new EconomySettings.Payments(true, new BigDecimal("0.01"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, Duration.ZERO)
    );

    @Test
    void acceptsAmountsExactlyRepresentableByTheCurrency() {
        assertEquals(new BigDecimal("1.00"), EconomyPersistenceValues.normalizePositive(new BigDecimal("1"), MONEY));
        assertEquals(new BigDecimal("1.20"), EconomyPersistenceValues.normalizePositive(new BigDecimal("1.20"), MONEY));
    }

    @Test
    void rejectsRatherThanRoundsAnOverPreciseMoneyRequest() {
        EconomyRejectedException failure = assertThrows(EconomyRejectedException.class,
                () -> EconomyPersistenceValues.normalizePositive(new BigDecimal("1.005"), MONEY));

        assertEquals(EconomyResultStatus.INVALID_AMOUNT, failure.status());
        assertEquals("Amount exceeds the configured currency precision", failure.getMessage());
    }
}
