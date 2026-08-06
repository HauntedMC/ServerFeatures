package nl.hauntedmc.serverfeatures.features.economy;

import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyTransferRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyApiValidationTest {

    private static final EconomyAccountRef ACCOUNT = new EconomyAccountRef(
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Player",
            "money",
            "hauntedmc/global"
    );

    @Test
    void acceptsPersistentIdentifiersAtTheirExactLimits() {
        assertDoesNotThrow(() -> new EconomyScope(EconomyScopeType.GLOBAL, "x".repeat(128)));
        assertDoesNotThrow(() -> new EconomyMutationRequest(
                "x".repeat(64),
                "x".repeat(160),
                ACCOUNT,
                BigDecimal.ONE,
                null,
                "system",
                "test",
                Map.of()
        ));
    }

    @Test
    void rejectsOversizedPersistentIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyScope(
                EconomyScopeType.GLOBAL,
                "x".repeat(129)
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyAccountRef(
                1L,
                ACCOUNT.playerUuid(),
                "Player",
                "x".repeat(65),
                "hauntedmc/global"
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyAccountRef(
                1L,
                ACCOUNT.playerUuid(),
                "Player",
                "money",
                "x".repeat(129)
        ));
    }

    @Test
    void rejectsIdentifiersThatWouldBeTruncatedInTheJournal() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyMutationRequest(
                "x".repeat(65),
                "operation",
                ACCOUNT,
                BigDecimal.ONE,
                null,
                "system",
                "test",
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyTransferRequest(
                "transfer",
                "x".repeat(161),
                ACCOUNT,
                new EconomyAccountRef(
                        2L,
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "Other",
                        "money",
                        "hauntedmc/global"
                ),
                BigDecimal.ONE,
                null,
                "system",
                "test",
                Map.of(),
                false
        ));
    }
    @Test
    void rejectsInvalidIdentityAndUnboundedMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyAccountRef(
                0L,
                ACCOUNT.playerUuid(),
                "Player",
                "money",
                "hauntedmc/global"
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyAccountRef(
                1L,
                ACCOUNT.playerUuid(),
                "x".repeat(33),
                "money",
                "hauntedmc/global"
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyMutationRequest(
                "Lottery With Spaces",
                "operation",
                ACCOUNT,
                BigDecimal.ONE,
                null,
                "system",
                "test",
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyMutationRequest(
                "lottery",
                "operation",
                ACCOUNT,
                BigDecimal.ONE,
                -1L,
                "system",
                "test",
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyMutationRequest(
                "lottery",
                "operation",
                ACCOUNT,
                BigDecimal.ONE,
                null,
                "system",
                "test",
                Map.of("key", "x".repeat(513))
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomyMutationRequest(
                "integration",
                "operation",
                ACCOUNT,
                BigDecimal.ONE,
                null,
                "system",
                "test",
                Map.of("transaction_type", "WITHDRAW")
        ));
    }

}
