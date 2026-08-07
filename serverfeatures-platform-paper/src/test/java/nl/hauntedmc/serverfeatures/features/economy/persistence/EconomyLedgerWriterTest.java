package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyLedgerWriterTest {

    @Test
    void usesOneStableLogicalIssuanceAccountPerCurrencyScope() {
        EconomySettings.Currency currency = currency("network/global");

        String accountId = EconomyLedgerWriter.systemIssuanceAccountId(currency);

        assertEquals(accountId, EconomyLedgerWriter.systemIssuanceAccountId(currency));
        assertTrue(accountId.startsWith("system:issuance:money:"));
    }

    private static EconomySettings.Currency currency(String scope) {
        return new EconomySettings.Currency("money", new EconomyScope(EconomyScopeType.GLOBAL, scope),
                new EconomySettings.Display("coin", "coins", "$", "{amount}", 2, true),
                new EconomySettings.Balances(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), false,
                        RoundingMode.HALF_UP),
                new EconomySettings.Commands("money", List.of(), true, true, true, true, true, true),
                new EconomySettings.Payments(true, new BigDecimal("0.01"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, Duration.ZERO));
    }
}
