package nl.hauntedmc.serverfeatures.features.playercount.internal.messaging;

import com.google.gson.Gson;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.playercount.PlayerCount;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountSnapshotStore;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventBusHandlerTest {

    @SuppressWarnings("unchecked")
    @Test
    void subscribesTypedSnapshotAndUpdatesStore() {
        Fixture fixture = fixture();

        fixture.handler().subscribe("counts");

        ArgumentCaptor<Consumer<PlayerCountSnapshotMessage>> captor = ArgumentCaptor.forClass(
                Consumer.class
        );
        verify(fixture.redisBus()).subscribe(
                eq("counts"),
                eq(PlayerCountSnapshotMessage.TYPE),
                eq(PlayerCountSnapshotMessage.class),
                captor.capture()
        );
        captor.getValue().accept(message(1L, 2));

        assertEquals(2, fixture.store().current().orElseThrow().network().online());
        assertEquals(1, fixture.store().current().orElseThrow().network().vanished());
    }

    @SuppressWarnings("unchecked")
    @Test
    void disableUnsubscribesAndRejectsLateCallbacks() {
        Fixture fixture = fixture();
        fixture.handler().subscribe("counts");
        ArgumentCaptor<Consumer<PlayerCountSnapshotMessage>> captor = ArgumentCaptor.forClass(
                Consumer.class
        );
        verify(fixture.redisBus()).subscribe(
                eq("counts"),
                eq(PlayerCountSnapshotMessage.TYPE),
                eq(PlayerCountSnapshotMessage.class),
                captor.capture()
        );
        captor.getValue().accept(message(1L, 2));

        fixture.handler().disable();
        captor.getValue().accept(message(2L, 4));

        verify(fixture.subscription()).unsubscribe();
        assertEquals(1L, fixture.store().current().orElseThrow().sequence());
        assertEquals(2, fixture.store().current().orElseThrow().network().online());
        assertThrows(IllegalStateException.class, () -> fixture.handler().subscribe("counts"));
    }

    private static Fixture fixture() {
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
        when(subscription.unsubscribe()).thenReturn(CompletableFuture.completedFuture(null));
        return new Fixture(
                redisBus,
                subscription,
                store,
                new EventBusHandler(feature, redisBus, store)
        );
    }

    private static PlayerCountSnapshotMessage message(long sequence, int online) {
        String json = """
                {
                  "schemaVersion": 1,
                  "publisherId": "proxy-1",
                  "publisherEpoch": "epoch-1",
                  "sequence": %d,
                  "publishedAtEpochMillis": %d,
                  "networkOnline": %d,
                  "networkVanished": 1,
                  "servers": {"survival": {"online": %d, "vanished": 1}}
                }
                """.formatted(sequence, System.currentTimeMillis(), online, online);
        return new Gson().fromJson(json, PlayerCountSnapshotMessage.class);
    }

    private record Fixture(
            MessagingDataAccess redisBus,
            Subscription subscription,
            PlayerCountSnapshotStore store,
            EventBusHandler handler
    ) {
    }
}
