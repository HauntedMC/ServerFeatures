package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyRepositoryTest {

    @Test
    void rejectsACurrencyWhenTheTransactionWrapsItsDefinitionFailure() {
        ORMContext orm = mock(ORMContext.class);
        when(orm.runInTransaction(any())).thenThrow(new IllegalStateException("Transaction rolled back",
                new EconomyDefinitionException("Currency definition mismatch")));

        EconomyRepository.DefinitionValidation validation = new EconomyRepository(orm).validateDefinitions(settings());

        assertFalse(validation.activeCurrencies().containsKey("money"));
        assertEquals("Currency definition mismatch", validation.rejectedCurrencies().get("money"));
    }

    @Test
    void preservesNonDefinitionStartupFailures() {
        ORMContext orm = mock(ORMContext.class);
        when(orm.runInTransaction(any())).thenThrow(new IllegalStateException("Database unavailable"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new EconomyRepository(orm).validateDefinitions(settings()));

        assertEquals("Database unavailable", failure.getMessage());
    }

    private static EconomySettings settings() {
        EconomySettings.Currency money = new EconomySettings.Currency("money",
                new EconomyScope(EconomyScopeType.SERVER, "hauntedmc/server/demo"),
                new EconomySettings.Display("coin", "coins", "$", "{symbol}{amount}", 2, true),
                new EconomySettings.Balances(new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("1000.00"), false, RoundingMode.HALF_UP),
                new EconomySettings.Commands("money", List.of(), true, true, true, true, true, false),
                new EconomySettings.Payments(true, new BigDecimal("0.01"), new BigDecimal("100.00"),
                        new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, Duration.ofSeconds(1)));
        return new EconomySettings("hauntedmc", "demo",
                new EconomySettings.Vault(false, "money", EconomySettings.VaultConflictPolicy.FAIL),
                new EconomySettings.Messaging(false, "hauntedmc", "serverfeatures.economy.balance"),
                new EconomySettings.Cache(Duration.ofSeconds(10)), Map.of("money", money));
    }
}
