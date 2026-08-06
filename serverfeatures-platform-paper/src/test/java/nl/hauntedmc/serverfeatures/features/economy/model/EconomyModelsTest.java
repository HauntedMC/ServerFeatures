package nl.hauntedmc.serverfeatures.features.economy.model;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.HistoryPage;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.VerificationReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyModelsTest {

    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void identityRequiresImmutableCanonicalIdentifiers() {
        Identity trimmed = new Identity(1L, PLAYER_UUID, "  Player  ");
        Identity fallback = new Identity(1L, PLAYER_UUID, " ");

        assertAll(
                () -> assertEquals("Player", trimmed.playerName()),
                () -> assertEquals(PLAYER_UUID.toString(), fallback.playerName()),
                () -> assertThrows(IllegalArgumentException.class, () -> new Identity(0L, PLAYER_UUID, "Player")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Identity(1L, null, "Player"))
        );
    }

    @Test
    void mutationOutcomeOnlyTreatsCommittedOrReplayResultsAsSuccessful() {
        assertTrue(outcome(EconomyResultStatus.SUCCESS).successful());
        assertTrue(outcome(EconomyResultStatus.IDEMPOTENT_REPLAY).successful());
        assertFalse(outcome(EconomyResultStatus.IDEMPOTENCY_CONFLICT).successful());
        assertFalse(outcome(EconomyResultStatus.TEMPORARY_FAILURE).successful());
    }

    @Test
    void historyPageDefensivelyCopiesEntries() {
        List<HistoryItem> mutable = new ArrayList<>();
        mutable.add(historyItem());
        HistoryPage page = new HistoryPage(mutable, 1, false);
        mutable.clear();

        assertEquals(1, page.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> page.entries().clear());
    }

    @Test
    void verificationHealthFailsForEveryIntegrityViolation() {
        assertTrue(report(0, 0, 0, 0, 0, 0, 0).healthy());
        assertAll(
                () -> assertFalse(report(1, 0, 0, 0, 0, 0, 0).healthy()),
                () -> assertFalse(report(0, 1, 0, 0, 0, 0, 0).healthy()),
                () -> assertFalse(report(0, 0, 1, 0, 0, 0, 0).healthy()),
                () -> assertFalse(report(0, 0, 0, 1, 0, 0, 0).healthy()),
                () -> assertFalse(report(0, 0, 0, 0, 1, 0, 0).healthy()),
                () -> assertFalse(report(0, 0, 0, 0, 0, 1, 0).healthy()),
                () -> assertFalse(report(0, 0, 0, 0, 0, 0, 1).healthy())
        );
    }

    private static MutationOutcome outcome(EconomyResultStatus status) {
        return new MutationOutcome(status, null, null, null, "", null, null);
    }

    private static HistoryItem historyItem() {
        return new HistoryItem(
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "TRANSFER",
                new BigDecimal("5.00"),
                new BigDecimal("15.00"),
                "Player",
                "Payment",
                123L
        );
    }

    private static VerificationReport report(
            long invalidBalances,
            long invalidEntries,
            long orphanSettings,
            long orphanEntries,
            long identityMismatches,
            long accountsWithoutEntries,
            long transactionsWithoutEntries
    ) {
        return new VerificationReport(
                10L,
                20L,
                invalidBalances,
                invalidEntries,
                orphanSettings,
                orphanEntries,
                identityMismatches,
                accountsWithoutEntries,
                transactionsWithoutEntries
        );
    }
}
