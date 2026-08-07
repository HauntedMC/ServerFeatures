package nl.hauntedmc.serverfeatures.features.economy.command;

import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentConfirmationTrackerTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Identity FIRST_RECIPIENT = new Identity(1L,
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "First");
    private static final Identity SECOND_RECIPIENT = new Identity(2L,
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), "Second");

    @Test
    void olderLookupCannotReplaceTheNewestPaymentConfirmation() {
        PaymentConfirmationTracker tracker = new PaymentConfirmationTracker();
        UUID firstAttempt = tracker.begin(PLAYER);
        UUID secondAttempt = tracker.begin(PLAYER);

        assertFalse(tracker.confirm(PLAYER, firstAttempt, FIRST_RECIPIENT, new BigDecimal("100.00")));
        assertTrue(tracker.confirm(PLAYER, secondAttempt, SECOND_RECIPIENT, new BigDecimal("200.00")));

        PaymentConfirmationTracker.PendingPayment pending = tracker.consume(PLAYER).orElseThrow();
        assertEquals(SECOND_RECIPIENT, pending.recipient());
        assertEquals(new BigDecimal("200.00"), pending.amount());
    }

    @Test
    void consumingAConfirmationInvalidatesAnUnfinishedLookup() {
        PaymentConfirmationTracker tracker = new PaymentConfirmationTracker();
        UUID attempt = tracker.begin(PLAYER);

        assertTrue(tracker.consume(PLAYER).isEmpty());
        assertFalse(tracker.confirm(PLAYER, attempt, FIRST_RECIPIENT, BigDecimal.ONE));
    }
}
