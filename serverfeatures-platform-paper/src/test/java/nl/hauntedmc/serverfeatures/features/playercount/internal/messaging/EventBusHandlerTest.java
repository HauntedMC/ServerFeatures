package nl.hauntedmc.serverfeatures.features.playercount.internal.messaging;

import com.google.gson.Gson;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.playercount.PlayerCount;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountSnapshotStore;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventBusHandlerTest {

    @SuppressWarnings("unchecked")
    @Test
    void subscribesTypedSnapshotAndUpdatesStore() {
        PlayerCount feature = mock(PlayerCount.class);
        MessagingDataAccess redisBus = mock(MessagingDataAccess.class);
        Subscription subscription = mock(Subscription.class);
        PlayerCountSnapshotStore store = new PlayerCountSnapshotStore(
                "survival",
                10_000L,
                "proxy-1"
        );
        when(redisBus.subscribe(
                eq("counts"),
                eq(PlayerCountSnapshotMessage.TYPE),
                eq(PlayerCountSnapshotMessage.class),
                org.mockito.ArgumentMatchers.<Consumer<PlayerCountSnapshotMessage>>any()
        )).thenReturn(subscription);
        EventBusHandler handler = new EventBusHandler(feature, redisBus, store);

        handler.subscribe("counts");

        ArgumentCaptor<Consumer<PlayerCountSnapshotMessage>> captor = ArgumentCaptor.forClass(
                Consumer.class
        );
        verify(redisBus).subscribe(
                eq("counts"),
                eq(PlayerCountSnapshotMessage.TYPE),
                eq(PlayerCountSnapshotMessage.class),
                captor.capture()
        );
        String json = """
                {
                  "schemaVersion": 1,
                  "publisherId": "proxy-1",
                  "publisherEpoch": "epoch-1",
                  "sequence": 1,
                  "publishedAtEpochMillis": %d,
                  "networkOnline": 2,
                  "networkVanished": 1,
                  "servers": {"survival": {"online": 2, "vanished": 1}}
                }
                """.formatted(System.currentTimeMillis());
        captor.getValue().accept(new Gson().fromJson(json, PlayerCountSnapshotMessage.class));

        assertEquals(2, store.current().orElseThrow().network().online());
        assertEquals(1, store.current().orElseThrow().network().vanished());
    }
}
