package nl.hauntedmc.serverfeatures.features.economy.vault;

import net.milkbowl.vault.economy.EconomyResponse;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.service.EconomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VaultEconomyProviderTest {
    private static final String CURRENCY_ID = "money";
    private static final Identity IDENTITY = new Identity(
            42L,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "Alice"
    );

    private EconomyService service;
    private VaultEconomyProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(EconomyService.class);
        when(service.requireCurrency(CURRENCY_ID)).thenReturn(currency());
        provider = new VaultEconomyProvider(service, CURRENCY_ID);
    }

    @Test
    void successfulMutationReportsTheRoundedAmountActuallyApplied() {
        Account account = account("11.01");
        MutationOutcome outcome = new MutationOutcome(
                EconomyResultStatus.SUCCESS,
                UUID.randomUUID(),
                account.balance(),
                null,
                "",
                account,
                null
        );
        when(service.resolveSync("Alice")).thenReturn(Optional.of(IDENTITY));
        when(service.mutateSync(
                eq(IDENTITY),
                eq(CURRENCY_ID),
                eq(new BigDecimal("1.01")),
                eq(TransactionType.DEPOSIT),
                eq("vault"),
                anyString()
        )).thenReturn(outcome);

        EconomyResponse response = provider.depositPlayer("Alice", 1.005D);

        assertTrue(response.transactionSuccess());
        assertEquals(1.01D, response.amount, 0.0D);
        assertEquals(11.01D, response.balance, 0.0D);
    }

    @Test
    void failedMutationReportsThatNoAmountWasModified() {
        MutationOutcome outcome = new MutationOutcome(
                EconomyResultStatus.INSUFFICIENT_FUNDS,
                null,
                new BigDecimal("1.00"),
                null,
                "Insufficient funds",
                null,
                null
        );
        when(service.resolveSync("Alice")).thenReturn(Optional.of(IDENTITY));
        when(service.mutateSync(
                eq(IDENTITY),
                eq(CURRENCY_ID),
                eq(new BigDecimal("5.00")),
                eq(TransactionType.WITHDRAW),
                eq("vault"),
                anyString()
        )).thenReturn(outcome);

        EconomyResponse response = provider.withdrawPlayer("Alice", 5.0D);

        assertFalse(response.transactionSuccess());
        assertEquals(0.0D, response.amount, 0.0D);
        assertEquals(1.0D, response.balance, 0.0D);
        assertEquals("Insufficient funds", response.errorMessage);
    }

    @Test
    void hasChecksHeldFundsInsteadOfConfiguredOverdraftCapacity() {
        when(service.resolveSync("Alice")).thenReturn(Optional.of(IDENTITY));
        when(service.balanceSync(IDENTITY, CURRENCY_ID)).thenReturn(Optional.of(account("0.00")));

        assertFalse(provider.has("Alice", 50.0D));
        assertTrue(provider.has("Alice", 0.0D));
    }

    @Test
    void amountBelowCurrencyPrecisionIsRejectedBeforePlayerLookup() {
        EconomyResponse response = provider.depositPlayer("Alice", 0.001D);

        assertFalse(response.transactionSuccess());
        assertEquals(0.0D, response.amount, 0.0D);
        assertEquals("Amount is smaller than the currency precision", response.errorMessage);
    }

    @Test
    void disabledProviderDoesNotCallTheSharedService() {
        EconomyService isolatedService = mock(EconomyService.class);
        VaultEconomyProvider disabled = new VaultEconomyProvider(isolatedService, CURRENCY_ID);
        disabled.disable();

        EconomyResponse response = disabled.depositPlayer("Alice", 1.0D);

        assertFalse(response.transactionSuccess());
        assertEquals(0.0D, response.amount, 0.0D);
        verifyNoInteractions(isolatedService);
    }

    @Test
    void rejectsAPrimaryCurrencyThatVaultDoublesCannotRepresentExactly() {
        when(service.requireCurrency(CURRENCY_ID)).thenReturn(new EconomySettings.Currency(
                CURRENCY_ID,
                new EconomyScope(EconomyScopeType.GLOBAL, "hauntedmc/global"),
                new EconomySettings.Display("coin", "coins", "$", "{symbol}{amount}", 2, true),
                new EconomySettings.Balances(BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("90071992547410.00"), false, RoundingMode.HALF_UP),
                new EconomySettings.Commands("money", List.of(), true, true, true, true, true, true),
                new EconomySettings.Payments(true, new BigDecimal("0.01"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, Duration.ZERO)
        ));

        assertThrows(IllegalArgumentException.class, () -> VaultEconomyProvider.validateDoubleCompatibility(
                service.requireCurrency(CURRENCY_ID)
        ));
    }

    private static Account account(String balance) {
        return new Account(
                "42:money:test",
                IDENTITY,
                CURRENCY_ID,
                "network/global",
                new BigDecimal(balance),
                1L,
                1L,
                true,
                AccountStatus.ACTIVE
        );
    }

    private static EconomySettings.Currency currency() {
        return new EconomySettings.Currency(
                CURRENCY_ID,
                new EconomyScope(EconomyScopeType.GLOBAL, "network/global"),
                new EconomySettings.Display("coin", "coins", "$", "{symbol}{amount}", 2, true),
                new EconomySettings.Balances(
                        BigDecimal.ZERO,
                        new BigDecimal("-100.00"),
                        new BigDecimal("1000.00"),
                        true,
                        RoundingMode.HALF_UP
                ),
                new EconomySettings.Commands("money", List.of(), true, true, true, true, true, true),
                new EconomySettings.Payments(
                        true,
                        new BigDecimal("0.01"),
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        Duration.ZERO
                )
        );
    }
}
