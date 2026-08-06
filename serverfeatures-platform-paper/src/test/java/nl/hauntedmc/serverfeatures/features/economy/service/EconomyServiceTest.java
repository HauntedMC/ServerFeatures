package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
