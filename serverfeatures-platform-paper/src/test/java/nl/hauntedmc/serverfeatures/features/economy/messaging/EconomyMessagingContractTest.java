package nl.hauntedmc.serverfeatures.features.economy.messaging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EconomyMessagingContractTest {

    @Test
    void redisMessagesNeverCarryAnAuthoritativeBalance() {
        assertFalse(Arrays.stream(EconomyBalanceMessage.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("balance") || field.getType() == BigDecimal.class));
        assertFalse(Arrays.stream(EconomyTransferMessage.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == BigDecimal.class));
    }

    @Test
    void invalidationMessageCarriesOnlyAuthoritativeReloadCoordinates() {
        String operationId = UUID.randomUUID().toString();
        EconomyBalanceMessage message = new EconomyBalanceMessage(
                "survival",
                operationId,
                42L,
                "00000000-0000-0000-0000-000000000042",
                "crowns",
                "hauntedmc/global",
                7L,
                3L,
                123456789L
        );

        assertAll(
                () -> assertEquals(EconomyBalanceMessage.SCHEMA_VERSION, message.getSchemaVersion()),
                () -> assertEquals("survival", message.getPublisherServer()),
                () -> assertEquals(operationId, message.getOperationId()),
                () -> assertEquals(42L, message.getPlayerId()),
                () -> assertEquals("00000000-0000-0000-0000-000000000042", message.getPlayerUuid()),
                () -> assertEquals("crowns", message.getCurrencyId()),
                () -> assertEquals("hauntedmc/global", message.getScopeKey()),
                () -> assertEquals(7L, message.getBalanceVersion()),
                () -> assertEquals(3L, message.getSettingsVersion()),
                () -> assertEquals(123456789L, message.getPublishedAt())
        );
    }

    @Test
    void transferMessageCarriesOnlyJournalVerificationCoordinates() {
        String operationId = UUID.randomUUID().toString();
        EconomyTransferMessage message = new EconomyTransferMessage(
                "skyblock",
                operationId,
                84L,
                "00000000-0000-0000-0000-000000000084",
                "credits",
                "hauntedmc/global",
                987654321L
        );

        assertAll(
                () -> assertEquals(EconomyTransferMessage.SCHEMA_VERSION, message.getSchemaVersion()),
                () -> assertEquals("skyblock", message.getPublisherServer()),
                () -> assertEquals(operationId, message.getOperationId()),
                () -> assertEquals(84L, message.getRecipientPlayerId()),
                () -> assertEquals("00000000-0000-0000-0000-000000000084", message.getRecipientPlayerUuid()),
                () -> assertEquals("credits", message.getCurrencyId()),
                () -> assertEquals("hauntedmc/global", message.getScopeKey()),
                () -> assertEquals(987654321L, message.getPublishedAt())
        );
    }
}
