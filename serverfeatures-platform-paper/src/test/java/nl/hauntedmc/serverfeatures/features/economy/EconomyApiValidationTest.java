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
}
