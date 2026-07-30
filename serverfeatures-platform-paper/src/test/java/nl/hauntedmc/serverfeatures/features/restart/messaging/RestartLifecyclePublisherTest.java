package nl.hauntedmc.serverfeatures.features.restart.messaging;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.PublishedDurableEvent;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestartLifecyclePublisherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparePersistsMarkerAndPublishesSortedPlayerSnapshot() throws Exception {
        Restart feature = feature();
        DurableMessagingDataAccess messaging = successfulMessaging();
        RestartMarkerStore store = store();
        RestartLifecyclePublisher publisher = publisher(feature, messaging, store, " Survival Main ");
        Player later = player("00000000-0000-0000-0000-000000000002");
        Player earlier = player("00000000-0000-0000-0000-000000000001");

        publisher.publishPrepare(List.of(later, earlier)).join();

        ArgumentCaptor<DurableEvent<RestartLifecycleMessage>> captor = eventCaptor();
        verify(messaging).publish(eq("server.restart.lifecycle"), captor.capture());
        DurableEvent<RestartLifecycleMessage> event = captor.getValue();
        RestartLifecycleMessage message = event.payload();
        assertEquals(RestartLifecycleMessage.ACTION_PREPARE, message.getAction());
        assertEquals("survival_main", message.getServerName());
        assertEquals(event.eventId(), event.processingKey());
        assertEquals(event.eventId(), message.getOperationId());
        assertEquals(
                List.of(
                        "00000000-0000-0000-0000-000000000001",
                        "00000000-0000-0000-0000-000000000002"
                ),
                message.getPlayerIds()
        );
        assertEquals(7_000L, message.getReconnectDelayMillis());
        assertEquals(300L, message.getPlayerIntervalMillis());

        RestartMarker marker = store.load().orElseThrow();
        assertEquals(message.getRestartId(), marker.restartId());
        assertEquals("survival_main", marker.serverName());
        assertEquals(7_000L, marker.reconnectDelayMillis());
        assertEquals(300L, marker.playerIntervalMillis());
        assertTrue(marker.expiresAtEpochMillis() > marker.createdAtEpochMillis());
    }

    @Test
    void readyIsPublishedFromPersistedMarkerAndThenDeletesIt() throws Exception {
        Restart feature = feature();
        DurableMessagingDataAccess messaging = successfulMessaging();
        RestartMarkerStore store = store();
        long now = System.currentTimeMillis();
        RestartMarker marker = new RestartMarker(
                "restart-ready",
                "creative",
                now,
                now + 60_000L,
                5_000L,
                250L
        );
        store.save(marker);
        RestartLifecyclePublisher publisher = publisher(feature, messaging, store, "ignored");

        publisher.publishReadyAfterServerLoad();

        ArgumentCaptor<DurableEvent<RestartLifecycleMessage>> captor = eventCaptor();
        verify(messaging).publish(eq("server.restart.lifecycle"), captor.capture());
        RestartLifecycleMessage message = captor.getValue().payload();
        assertEquals(RestartLifecycleMessage.ACTION_READY, message.getAction());
        assertEquals("restart-ready", message.getRestartId());
        assertEquals("creative", message.getServerName());
        assertEquals(List.of(), message.getPlayerIds());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void expiredMarkerIsDeletedWithoutPublishingReady() throws Exception {
        Restart feature = feature();
        DurableMessagingDataAccess messaging = mock(DurableMessagingDataAccess.class);
        RestartMarkerStore store = store();
        long now = System.currentTimeMillis();
        store.save(new RestartMarker(
                "restart-expired",
                "survival",
                now - 2_000L,
                now - 1_000L,
                0L,
                0L
        ));
        RestartLifecyclePublisher publisher = publisher(feature, messaging, store, "survival");

        publisher.publishReadyAfterServerLoad();

        verify(messaging, never()).publish(any(), any());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void closedPublisherRejectsNewPrepareWithoutWritingMarker() throws Exception {
        Restart feature = feature();
        DurableMessagingDataAccess messaging = successfulMessaging();
        RestartMarkerStore store = store();
        RestartLifecyclePublisher publisher = publisher(feature, messaging, store, "survival");

        publisher.close();
        CompletableFuture<PublishedDurableEvent> result = publisher.publishPrepare(null);

        assertTrue(result.isCompletedExceptionally());
        assertFalse(store.load().isPresent());
        verify(messaging, never()).publish(any(), any());
    }

    private RestartLifecyclePublisher publisher(
            Restart feature,
            DurableMessagingDataAccess messaging,
            RestartMarkerStore store,
            String serverName
    ) {
        return new RestartLifecyclePublisher(
                feature,
                messaging,
                store,
                "server.restart.lifecycle",
                serverName
        );
    }

    private Restart feature() {
        Restart feature = mock(Restart.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getPositiveInt("autoreconnect.wait_after_ready_seconds", 5)).thenReturn(7);
        when(feature.getPositiveLong("autoreconnect.player_interval_millis", 250L)).thenReturn(300L);
        when(feature.getPositiveInt("autoreconnect.session_ttl_seconds", 600)).thenReturn(120);
        when(feature.getPositiveInt("autoreconnect.ready_publish_attempts", 12)).thenReturn(3);
        when(feature.getPositiveInt("autoreconnect.ready_retry_seconds", 5)).thenReturn(1);
        return feature;
    }

    private DurableMessagingDataAccess successfulMessaging() {
        DurableMessagingDataAccess messaging = mock(DurableMessagingDataAccess.class);
        PublishedDurableEvent published = mock(PublishedDurableEvent.class);
        CompletableFuture<PublishedDurableEvent> completed = CompletableFuture.completedFuture(published);
        when(messaging.publish(any(), any())).thenReturn(completed);
        return messaging;
    }

    private RestartMarkerStore store() {
        return new RestartMarkerStore(temporaryDirectory.resolve(UUID.randomUUID() + ".properties"));
    }

    private Player player(String id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString(id));
        return player;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<DurableEvent<RestartLifecycleMessage>> eventCaptor() {
        return ArgumentCaptor.forClass(DurableEvent.class);
    }
}
