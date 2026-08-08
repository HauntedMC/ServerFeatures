package nl.hauntedmc.serverfeatures.api.economy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyContractsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void accountAndScopeNormalizeStableIdentifiers() {
        EconomyAccountRef account = new EconomyAccountRef(7L, PLAYER, "  Player  ", " CrOwNs ", " network ");
        EconomyScope scope = new EconomyScope(EconomyScopeType.GLOBAL, " network ");

        assertEquals(7L, account.playerId());
        assertEquals(PLAYER, account.playerUuid());
        assertEquals("Player", account.playerName());
        assertEquals("crowns", account.currencyId());
        assertEquals("network", account.scopeKey());
        assertEquals(EconomyScopeType.GLOBAL, scope.type());
        assertEquals("network", scope.key());

        assertThrows(NullPointerException.class, () -> new EconomyAccountRef(null, null, null, "crowns", null));
        assertThrows(IllegalArgumentException.class, () -> new EconomyAccountRef(0L, PLAYER, null, "crowns", null));
        assertThrows(IllegalArgumentException.class, () -> new EconomyAccountRef(null, PLAYER, null, " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyAccountRef(null, PLAYER, null, "x".repeat(65), null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyAccountRef(null, PLAYER, "x".repeat(33), "crowns", null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyAccountRef(null, PLAYER, null, "crowns", "x".repeat(129)));
        assertThrows(NullPointerException.class, () -> new EconomyScope(null, "network"));
        assertThrows(IllegalArgumentException.class, () -> new EconomyScope(EconomyScopeType.GLOBAL, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyScope(EconomyScopeType.GLOBAL, "x".repeat(129)));
    }

    @Test
    void currencyAndBalanceRemainImmutableDomainValues() {
        EconomyScope scope = new EconomyScope(EconomyScopeType.GLOBAL, "network");
        EconomyCurrency currency = new EconomyCurrency(
                " Crowns ", null, null, null, 2, scope,
                BigDecimal.ZERO, new BigDecimal("1000000"), true
        );
        EconomyAccountRef account = account();
        EconomyBalance balance = new EconomyBalance(account, new BigDecimal("12.50"), 4L);

        assertEquals("crowns", currency.id());
        assertEquals("crowns", currency.singular());
        assertEquals("crowns", currency.plural());
        assertEquals("", currency.symbol());
        assertEquals(2, currency.fractionalDigits());
        assertTrue(currency.paymentsEnabled());
        assertEquals(new BigDecimal("12.50"), balance.balance());
        assertEquals(4L, balance.version());

        assertThrows(IllegalArgumentException.class,
                () -> new EconomyCurrency(" ", null, null, null, 0, scope, BigDecimal.ZERO, BigDecimal.ONE, true));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyCurrency("x", null, null, null, -1, scope, BigDecimal.ZERO, BigDecimal.ONE, true));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyCurrency("x", null, null, null, 9, scope, BigDecimal.ZERO, BigDecimal.ONE, true));
        assertThrows(NullPointerException.class,
                () -> new EconomyCurrency("x", null, null, null, 0, null, BigDecimal.ZERO, BigDecimal.ONE, true));
        assertThrows(NullPointerException.class, () -> new EconomyBalance(null, BigDecimal.ZERO, 1));
        assertThrows(NullPointerException.class, () -> new EconomyBalance(account, null, 1));
    }

    @Test
    void mutationAndTransferRequestsNormalizeAndDefensivelyCopyMetadata() {
        EconomyAccountRef account = account();
        EconomyAccountRef recipient = new EconomyAccountRef(
                8L,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Recipient",
                "crowns",
                "network"
        );
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(" item ", " crate ");

        EconomyMutationRequest mutation = new EconomyMutationRequest(
                " Lottery:Purchase ", "purchase-1", account, BigDecimal.TEN,
                7L, " Admin ", " Reason ", metadata
        );
        EconomyTransferRequest transfer = new EconomyTransferRequest(
                " Player.Pay ", "pay-1", account, recipient, BigDecimal.ONE,
                7L, " Admin ", " Transfer ", metadata, false
        );
        metadata.put("late", "mutation");

        assertEquals("lottery:purchase", mutation.source());
        assertEquals("Admin", mutation.actorName());
        assertEquals("Reason", mutation.reason());
        assertEquals(Map.of("item", "crate"), mutation.metadata());
        assertEquals("player.pay", transfer.source());
        assertEquals(Map.of("item", "crate"), transfer.metadata());
        assertFalse(transfer.bypassPaymentsToggle());

        assertThrows(IllegalArgumentException.class,
                () -> new EconomyMutationRequest("bad source!", "id", account, BigDecimal.ONE,
                        null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyMutationRequest("source", " ", account, BigDecimal.ONE,
                        null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyMutationRequest("source", "id", account, BigDecimal.ONE,
                        0L, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new EconomyTransferRequest("source", "id", null, recipient, BigDecimal.ONE,
                        null, null, null, null, false));
    }

    @Test
    void metadataValidationRejectsReservedOversizedAndInvalidInputs() {
        assertEquals(Map.of(), EconomyRequestValidation.metadata(null));
        assertEquals("source.name", EconomyRequestValidation.source(" Source.Name "));
        assertEquals("event.type", EconomyRequestValidation.eventType(" Event.Type "));
        assertEquals("", EconomyRequestValidation.text(null, "optional", 3, false));

        assertThrows(IllegalArgumentException.class,
                () -> EconomyRequestValidation.eventType("bad event!"));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyRequestValidation.text(null, "required", 3, true));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyRequestValidation.text("abcd", "short", 3, false));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyRequestValidation.metadata(Map.of("transaction_type", "purchase")));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyRequestValidation.metadata(Map.of("x".repeat(65), "value")));
        assertThrows(IllegalArgumentException.class,
                () -> EconomyRequestValidation.metadata(Map.of("key", "x".repeat(513))));

        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < 33; index++) {
            tooMany.put("k" + index, "v");
        }
        assertThrows(IllegalArgumentException.class, () -> EconomyRequestValidation.metadata(tooMany));

        Map<String, String> tooLong = new LinkedHashMap<>();
        for (int index = 0; index < 5; index++) {
            tooLong.put("key" + index, "x".repeat(500));
        }
        assertThrows(IllegalArgumentException.class, () -> EconomyRequestValidation.metadata(tooLong));
    }

    @Test
    void resultAndWorkflowContractsExposeCommittedState() {
        EconomyResult success = new EconomyResult(
                EconomyResultStatus.SUCCESS,
                UUID.randomUUID(),
                BigDecimal.TEN,
                null,
                null
        );
        EconomyResult replay = new EconomyResult(
                EconomyResultStatus.IDEMPOTENT_REPLAY,
                UUID.randomUUID(),
                BigDecimal.TEN,
                null,
                "replay"
        );
        EconomyResult rejected = new EconomyResult(
                EconomyResultStatus.INSUFFICIENT_FUNDS,
                null,
                BigDecimal.ZERO,
                null,
                "rejected"
        );

        assertTrue(success.successful());
        assertFalse(success.replayed());
        assertTrue(replay.successful());
        assertTrue(replay.replayed());
        assertFalse(rejected.successful());
        assertEquals("", success.message());
        assertThrows(NullPointerException.class, () -> new EconomyResult(null, null, null, null, null));

        EconomyWorkflowRef workflow = new EconomyWorkflowRef(" Lottery ", " purchase-42 ");
        EconomyWorkflowEvent event = new EconomyWorkflowEvent(
                UUID.randomUUID(), workflow, UUID.randomUUID(), account(), BigDecimal.ONE,
                " Grant.Reward ", Map.of("reward", "ticket"), 10L
        );
        EconomyWorkflowRequest request = new EconomyWorkflowRequest(
                workflow, account(), BigDecimal.ONE, 7L, "Actor", "purchase",
                "Grant.Reward", Map.of("reward", "ticket")
        );
        EconomyWorkflowResult result = new EconomyWorkflowResult(
                success, EconomyWorkflowState.PENDING_FULFILMENT, event.eventId(), 0, null
        );

        assertEquals("lottery", workflow.source());
        assertEquals("purchase-42", workflow.workflowId());
        assertEquals("grant.reward", event.eventType());
        assertEquals("grant.reward", request.eventType());
        assertTrue(result.charged());
        assertEquals("", result.lastError());

        assertThrows(IllegalArgumentException.class,
                () -> new EconomyWorkflowEvent(UUID.randomUUID(), workflow, UUID.randomUUID(), account(),
                        BigDecimal.ONE, "event", Map.of(), -1L));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyWorkflowRequest(workflow, account(), BigDecimal.ONE, 0L,
                        null, null, "event", null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyWorkflowResult(success, EconomyWorkflowState.DELIVERED, event.eventId(), -1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyWorkflowResult(success, EconomyWorkflowState.DELIVERED, null, 0, null));
        assertFalse(new EconomyWorkflowResult(
                rejected, EconomyWorkflowState.DEAD_LETTER, null, 1, "failed").charged());
    }

    @Test
    void enumContractsRemainStable() {
        assertEquals(3, EconomyScopeType.values().length);
        assertEquals(EconomyScopeType.GLOBAL, EconomyScopeType.valueOf("GLOBAL"));
        assertEquals(11, EconomyResultStatus.values().length);
        assertEquals(EconomyResultStatus.IDEMPOTENCY_CONFLICT,
                EconomyResultStatus.valueOf("IDEMPOTENCY_CONFLICT"));
        assertEquals(3, EconomyWorkflowState.values().length);
        assertEquals(EconomyWorkflowState.DEAD_LETTER, EconomyWorkflowState.valueOf("DEAD_LETTER"));
    }

    private static EconomyAccountRef account() {
        return new EconomyAccountRef(7L, PLAYER, "Player", "crowns", "network");
    }
}
