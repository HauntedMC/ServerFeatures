package nl.hauntedmc.serverfeatures.features.lottery.economy;

import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyCurrency;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResult;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRequest;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuiltinLotteryEconomyTest {

    @Test
    void requiresCurrencyPrecisionThatMatchesLotteryStorage() {
        EconomyApi economy = mock(EconomyApi.class);
        when(economy.currency("money")).thenReturn(Optional.of(currency(2)));
        when(economy.currency("crowns")).thenReturn(Optional.of(currency(0)));

        assertDoesNotThrow(() -> new BuiltinLotteryEconomy(economy, "money"));
        assertThrows(IllegalStateException.class, () -> new BuiltinLotteryEconomy(economy, "crowns"));
    }


    @Test
    void retriesTemporaryNativeFailureWithTheSameIdempotentRequest() {
        EconomyApi economy = mock(EconomyApi.class);
        when(economy.currency("money")).thenReturn(Optional.of(currency(2)));
        when(economy.withdraw(any()))
                .thenReturn(CompletableFuture.completedFuture(new EconomyResult(
                        EconomyResultStatus.TEMPORARY_FAILURE, null, null, null, "temporary"
                )))
                .thenReturn(CompletableFuture.completedFuture(new EconomyResult(
                        EconomyResultStatus.SUCCESS,
                        UUID.fromString("00000000-0000-0000-0000-000000000010"),
                        new BigDecimal("90.00"),
                        null,
                        ""
                )));
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(player.getName()).thenReturn("Player");

        LotteryEconomyGateway.EconomyResult result = new BuiltinLotteryEconomy(economy, "money")
                .withdraw(
                        player,
                        Money.of(new BigDecimal("10.00")),
                        LotteryEconomyGateway.Operation.PURCHASE,
                        "purchase:test"
                )
                .toCompletableFuture()
                .join();

        assertTrue(result.successful());
        ArgumentCaptor<EconomyMutationRequest> requests = ArgumentCaptor.forClass(EconomyMutationRequest.class);
        verify(economy, times(2)).withdraw(requests.capture());
        assertEquals(requests.getAllValues().getFirst(), requests.getAllValues().getLast());
        EconomyMutationRequest request = requests.getValue();
        assertEquals("lottery", request.source());
        assertEquals("Lottery ticket purchase", request.reason());
        assertEquals("purchase", request.metadata().get("lottery_operation"));
    }

    @Test
    void rejectsLotteryOperationsWithTheWrongEconomyDirection() {
        EconomyApi economy = mock(EconomyApi.class);
        when(economy.currency("money")).thenReturn(Optional.of(currency(2)));
        OfflinePlayer player = mock(OfflinePlayer.class);

        BuiltinLotteryEconomy backend = new BuiltinLotteryEconomy(economy, "money");

        assertThrows(IllegalArgumentException.class, () -> backend.withdraw(
                player,
                Money.of(new BigDecimal("10.00")),
                LotteryEconomyGateway.Operation.PAYOUT,
                "payout:test"
        ));
        assertThrows(IllegalArgumentException.class, () -> backend.deposit(
                player,
                Money.of(new BigDecimal("10.00")),
                LotteryEconomyGateway.Operation.DONATION,
                "donation:test"
        ));
    }

    @Test
    void chargesNativePurchasesThroughTheDurableWorkflowApi() {
        EconomyApi economy = mock(EconomyApi.class);
        when(economy.currency("money")).thenReturn(Optional.of(currency(2)));
        when(economy.chargeAndDispatch(any())).thenReturn(CompletableFuture.completedFuture(
                new nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowResult(
                        new EconomyResult(EconomyResultStatus.SUCCESS,
                                UUID.fromString("00000000-0000-0000-0000-000000000010"), null, null, ""),
                        nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowState.PENDING_FULFILMENT,
                        UUID.fromString("00000000-0000-0000-0000-000000000011"), 0, ""
                )
        ));

        new BuiltinLotteryEconomy(economy, "money").chargePurchase(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Player", 42L,
                Money.of(new BigDecimal("10.00")), "00000000-0000-0000-0000-000000000012"
        ).toCompletableFuture().join();

        ArgumentCaptor<EconomyWorkflowRequest> requests = ArgumentCaptor.forClass(EconomyWorkflowRequest.class);
        verify(economy).chargeAndDispatch(requests.capture());
        EconomyWorkflowRequest request = requests.getValue();
        assertEquals("lottery", request.workflow().source());
        assertEquals("00000000-0000-0000-0000-000000000012", request.workflow().workflowId());
        assertEquals("lottery.purchase.v1", request.eventType());
        assertEquals("00000000-0000-0000-0000-000000000012", request.metadata().get("purchase_intent_id"));
    }

    private static EconomyCurrency currency(int fractionalDigits) {
        return new EconomyCurrency(
                "money",
                "coin",
                "coins",
                "$",
                fractionalDigits,
                new EconomyScope(EconomyScopeType.SERVER, "hauntedmc/server/survival"),
                BigDecimal.ZERO,
                new BigDecimal("1000000000.00"),
                true
        );
    }
}
