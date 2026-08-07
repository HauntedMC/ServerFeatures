package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EconomyRequestFingerprintTest {
    private static final EconomySettings.Currency CURRENCY = currency();
    private static final Identity SENDER = new Identity(
            1L,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Sender"
    );
    private static final Identity RECIPIENT = new Identity(
            2L,
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "Recipient"
    );

    @Test
    void canonicalizesMetadataOrderButBindsAuditFields() {
        Map<String, String> leftMetadata = new LinkedHashMap<>();
        leftMetadata.put("round", "12");
        leftMetadata.put("type", "purchase");
        Map<String, String> rightMetadata = new LinkedHashMap<>();
        rightMetadata.put("type", "purchase");
        rightMetadata.put("round", "12");

        String left = EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                SENDER,
                CURRENCY,
                new BigDecimal("10.00"),
                1L,
                "Sender",
                "External purchase",
                leftMetadata,
                false
        );
        String right = EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                SENDER,
                CURRENCY,
                new BigDecimal("10.00"),
                1L,
                "Sender",
                "External purchase",
                rightMetadata,
                false
        );

        assertEquals(left, right);
        assertNotEquals(left, EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                SENDER,
                CURRENCY,
                new BigDecimal("11.00"),
                1L,
                "Sender",
                "External purchase",
                rightMetadata,
                false
        ));
        assertNotEquals(left, EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                SENDER,
                CURRENCY,
                new BigDecimal("10.00"),
                1L,
                "Sender",
                "Changed reason",
                rightMetadata,
                false
        ));
    }

    @Test
    void transferFingerprintBindsRecipientAndBypassPolicy() {
        String normal = EconomyRequestFingerprint.transfer(
                SENDER,
                RECIPIENT,
                CURRENCY,
                new BigDecimal("25.00"),
                1L,
                "Sender",
                "Player payment",
                Map.of(),
                false,
                false
        );
        Identity otherRecipient = new Identity(
                3L,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "Other"
        );

        assertNotEquals(normal, EconomyRequestFingerprint.transfer(
                SENDER,
                otherRecipient,
                CURRENCY,
                new BigDecimal("25.00"),
                1L,
                "Sender",
                "Player payment",
                Map.of(),
                false,
                false
        ));
        assertNotEquals(normal, EconomyRequestFingerprint.transfer(
                SENDER,
                RECIPIENT,
                CURRENCY,
                new BigDecimal("25.00"),
                1L,
                "Sender",
                "Player payment",
                Map.of(),
                true,
                false
        ));
    }

    @Test
    void metadataFramingCannotCollideAndIdentityUuidIsBound() {
        String metadataKeyContainsEquals = EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                SENDER,
                CURRENCY,
                new BigDecimal("1.00"),
                1L,
                "Sender",
                "test",
                Map.of("a=b", "c"),
                false
        );
        String metadataValueContainsEquals = EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                SENDER,
                CURRENCY,
                new BigDecimal("1.00"),
                1L,
                "Sender",
                "test",
                Map.of("a", "b=c"),
                false
        );
        Identity samePlayerIdDifferentUuid = new Identity(
                SENDER.playerId(),
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                SENDER.playerName()
        );
        String differentUuid = EconomyRequestFingerprint.mutation(
                TransactionType.WITHDRAW,
                TransactionType.WITHDRAW,
                samePlayerIdDifferentUuid,
                CURRENCY,
                new BigDecimal("1.00"),
                1L,
                "Sender",
                "test",
                Map.of("a=b", "c"),
                false
        );

        assertNotEquals(metadataKeyContainsEquals, metadataValueContainsEquals);
        assertNotEquals(metadataKeyContainsEquals, differentUuid);
    }

    private static EconomySettings.Currency currency() {
        return new EconomySettings.Currency(
                "crowns",
                new EconomyScope(EconomyScopeType.GLOBAL, "hauntedmc/global"),
                new EconomySettings.Display("crown", "crowns", "", "{amount}", 2, true),
                new EconomySettings.Balances(
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("1000000.00"),
                        false,
                        RoundingMode.HALF_UP
                ),
                new EconomySettings.Commands("crowns", List.of(), true, true, true, true, true, true),
                new EconomySettings.Payments(
                        true,
                        new BigDecimal("0.01"),
                        new BigDecimal("10000.00"),
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2),
                        Duration.ofSeconds(1)
                )
        );
    }
}
