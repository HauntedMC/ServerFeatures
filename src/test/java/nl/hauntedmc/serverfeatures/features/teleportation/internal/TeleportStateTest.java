package nl.hauntedmc.serverfeatures.features.teleportation.internal;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportStateTest {

    @Test
    void usesConfiguredCooldownPerAction() {
        TeleportState state = new TeleportState(path -> Map.of(
                "cooldown_seconds.randomtp", 30,
                "cooldown_seconds.tppos", 12
        ).get(path));

        assertEquals(30, state.getCooldownSeconds(TeleportAction.RANDOM_TP));
        assertEquals(12, state.getCooldownSeconds(TeleportAction.TP_POS));
    }

    @Test
    void remainingCooldownAndTryStartRespectStoredTimestamps() {
        TeleportState state = new TeleportState(path -> 10);
        UUID player = UUID.randomUUID();

        assertTrue(state.tryStart(player, TeleportAction.RANDOM_TP, 1_000L));
        assertFalse(state.tryStart(player, TeleportAction.RANDOM_TP, 5_000L));
        assertEquals(6L, state.remainingCooldownSeconds(player, TeleportAction.RANDOM_TP, 5_000L));
        assertEquals(0L, state.remainingCooldownSeconds(player, TeleportAction.RANDOM_TP, 11_000L));
    }

    @Test
    void setLastUseWithNonPositiveValueResetsCooldownEntry() {
        TeleportState state = new TeleportState(path -> 20);
        UUID player = UUID.randomUUID();

        state.setLastUse(player, TeleportAction.TP_POS, 3_000L);
        assertEquals(3_000L, state.getLastUse(player, TeleportAction.TP_POS));

        state.setLastUse(player, TeleportAction.TP_POS, 0L);
        assertEquals(0L, state.getLastUse(player, TeleportAction.TP_POS));
    }

    @Test
    void clearAllRemovesAllCooldowns() {
        TeleportState state = new TeleportState(path -> 10);
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();

        state.setLastUse(one, TeleportAction.RANDOM_TP, 1_000L);
        state.setLastUse(two, TeleportAction.TP_POS, 2_000L);
        state.clearAll();

        assertEquals(0L, state.getLastUse(one, TeleportAction.RANDOM_TP));
        assertEquals(0L, state.getLastUse(two, TeleportAction.TP_POS));
    }
}

