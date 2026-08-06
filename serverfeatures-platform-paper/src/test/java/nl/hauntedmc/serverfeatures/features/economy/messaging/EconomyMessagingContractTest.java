package nl.hauntedmc.serverfeatures.features.economy.messaging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EconomyMessagingContractTest {

    @Test
    void redisMessagesNeverCarryAnAuthoritativeBalance() {
        assertFalse(Arrays.stream(EconomyBalanceMessage.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("balance") || field.getType() == BigDecimal.class));
        assertFalse(Arrays.stream(EconomyTransferMessage.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == BigDecimal.class));
    }
}
