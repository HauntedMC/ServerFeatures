package nl.hauntedmc.serverfeatures.features.restart.messaging;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestartLifecycleMessageTest {

    @Test
    void constructorCopiesNormalizesAndExposesTheWirePayload() {
        List<String> playerIds = new ArrayList<>(List.of("first"));
        RestartLifecycleMessage message = new RestartLifecycleMessage(
                "restart.id.prepare",
                "id",
                RestartLifecycleMessage.ACTION_PREPARE,
                " survival ",
                100L,
                200L,
                -1L,
                -2L,
                playerIds
        );
        playerIds.add("second");

        assertEquals(RestartLifecycleMessage.TYPE, message.getType());
        assertEquals("restart.id.prepare", message.getOperationId());
        assertEquals("id", message.getRestartId());
        assertEquals(RestartLifecycleMessage.ACTION_PREPARE, message.getAction());
        assertEquals("survival", message.getServerName());
        assertEquals(100L, message.getCreatedAtEpochMillis());
        assertEquals(200L, message.getExpiresAtEpochMillis());
        assertEquals(0L, message.getReconnectDelayMillis());
        assertEquals(0L, message.getPlayerIntervalMillis());
        assertEquals(List.of("first"), message.getPlayerIds());
    }

    @Test
    void gsonRoundTripPreservesCompatibilityWithProxyFeatures() {
        Gson gson = new Gson();
        RestartLifecycleMessage original = new RestartLifecycleMessage(
                "restart.id.ready",
                "id",
                RestartLifecycleMessage.ACTION_READY,
                "creative",
                10L,
                20L,
                5_000L,
                250L,
                List.of()
        );

        RestartLifecycleMessage roundTrip = gson.fromJson(
                gson.toJson(original),
                RestartLifecycleMessage.class
        );

        assertEquals(RestartLifecycleMessage.TYPE, roundTrip.getType());
        assertEquals(original.getOperationId(), roundTrip.getOperationId());
        assertEquals(original.getRestartId(), roundTrip.getRestartId());
        assertEquals(original.getAction(), roundTrip.getAction());
        assertEquals(original.getServerName(), roundTrip.getServerName());
        assertEquals(original.getExpiresAtEpochMillis(), roundTrip.getExpiresAtEpochMillis());
        assertEquals(original.getReconnectDelayMillis(), roundTrip.getReconnectDelayMillis());
        assertEquals(original.getPlayerIntervalMillis(), roundTrip.getPlayerIntervalMillis());
        assertEquals(List.of(), roundTrip.getPlayerIds());
        assertEquals(List.of(), gson.fromJson("{}", RestartLifecycleMessage.class).getPlayerIds());
    }

    @Test
    void constructorRejectsUnsafeLifecycleMetadata() {
        assertThrows(IllegalArgumentException.class, () -> message("bad id", "id", "READY", "hub"));
        assertThrows(IllegalArgumentException.class, () -> message("restart.id.ready", "bad id", "READY", "hub"));
        assertThrows(IllegalArgumentException.class, () -> message("restart.id.ready", "id", "OTHER", "hub"));
        assertThrows(IllegalArgumentException.class, () -> message("restart.id.ready", "id", "READY", " "));
    }

    private RestartLifecycleMessage message(
            String operationId,
            String restartId,
            String action,
            String serverName
    ) {
        return new RestartLifecycleMessage(
                operationId,
                restartId,
                action,
                serverName,
                1L,
                2L,
                0L,
                0L,
                null
        );
    }
}
