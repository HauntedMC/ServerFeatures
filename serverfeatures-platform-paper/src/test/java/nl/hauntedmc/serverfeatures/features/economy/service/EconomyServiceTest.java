package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.AccountStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EconomyServiceTest {

    @Test
    void acceptsCompatibleLotteryJournalClassification() {
        assertEquals(
                TransactionType.LOTTERY_DONATION,
                EconomyService.requestedJournalType(
                        TransactionType.WITHDRAW,
                        Map.of("transaction_type", "LOTTERY_DONATION")
                )
        );
    }

    @Test
    void rejectsJournalClassificationThatWouldChangeMutationDirection() {
        assertEquals(
                TransactionType.WITHDRAW,
                EconomyService.requestedJournalType(
                        TransactionType.WITHDRAW,
                        Map.of("transaction_type", "LOTTERY_PAYOUT")
                )
        );
    }

    @Test
    void ignoresUnknownJournalClassification() {
        assertEquals(
                TransactionType.DEPOSIT,
                EconomyService.requestedJournalType(
                        TransactionType.DEPOSIT,
                        Map.of("transaction_type", "not-a-transaction-type")
                )
        );
    }

    @Test
    void mergesBalanceAndSettingsUsingIndependentVersions() {
        Identity identity = new Identity(
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Player"
        );
        Account newerBalance = new Account(
                "account",
                identity,
                "crowns",
                "hauntedmc/global",
                new BigDecimal("200.00"),
                5L,
                2L,
                true,
                AccountStatus.ACTIVE
        );
        Account newerSettings = new Account(
                "account",
                identity,
                "crowns",
                "hauntedmc/global",
                new BigDecimal("100.00"),
                4L,
                3L,
                false,
                AccountStatus.FROZEN
        );

        Account merged = EconomyService.mergeAccount(newerBalance, newerSettings);

        assertEquals(new BigDecimal("200.00"), merged.balance());
        assertEquals(5L, merged.version());
        assertEquals(3L, merged.settingsVersion());
        assertEquals(false, merged.paymentsEnabled());
        assertSame(AccountStatus.FROZEN, merged.status());
    }

}
