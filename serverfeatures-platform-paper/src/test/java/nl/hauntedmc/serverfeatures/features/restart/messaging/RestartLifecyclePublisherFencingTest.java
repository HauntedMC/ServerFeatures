package nl.hauntedmc.serverfeatures.features.restart.messaging;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.PublishedDurableEvent;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestartLifecyclePublisherFencingTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void staleCancellationCannotDeleteReplacementRestartMarker() throws Exception {
        Restart feature = feature();
        DurableMessagingDataAccess messaging = successfulMessaging();
        RestartMarkerStore store = store("cancel.properties");
        long now = System.currentTimeMillis();
        RestartMarker stale = marker("stale-restart", now);
        RestartMarker replacement = marker("replacement-restart", now + 1L);
        store.save(replacement);

        RestartLifecyclePublisher publisher = publisher(feature, messaging, store);
        publisher.publishCancel(stale).join();

        assertEquals(replacement, store.load().orElseThrow());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DurableEvent<RestartLifecycleMessage>> eventCaptor =
                ArgumentCaptor.forClass(DurableEvent.class);
        verify(messaging).publish(eq("server.restart.lifecycle"), eventCaptor.capture());
        assertEquals("stale-restart", eventCaptor.getValue().payload().getRestartId());
        assertEquals(
                RestartLifecycleMessage.ACTION_CANCEL,
                eventCaptor.getValue().payload().getAction()
        );
    }

    @Test
    void lateReadyCompletionCannotDeleteReplacementRestartMarker() throws Exception {
        Restart feature = feature();
        DurableMessagingDataAccess messaging = mock(DurableMessagingDataAccess.class);
        CompletableFuture<PublishedDurableEvent> pendingPublication = new CompletableFuture<>();
        when(messaging.publish(any(), any())).thenReturn(pendingPublication);
        RestartMarkerStore store = store("ready.properties");
        long now = System.currentTimeMillis();
        RestartMarker ready = marker("ready-restart", now);
        RestartMarker replacement = marker("replacement-restart", now + 1L);
        store.save(ready);
        RestartLifecyclePublisher publisher = publisher(feature, messaging, store);

        publisher.publishReadyAfterServerLoad();
        store.save(replacement);
        pendingPublication.complete(mock(PublishedDurableEvent.class));

        assertEquals(replacement, store.load().orElseThrow());
    }

    private Restart feature() {
        Restart feature = mock(Restart.class);
        when(feature.getLogger()).thenReturn(mock(FeatureLogger.class));
        when(feature.getPositiveInt("autoreconnect.wait_after_ready_seconds", 5)).thenReturn(5);
        when(feature.getPositiveLong("autoreconnect.player_interval_millis", 250L)).thenReturn(250L);
        when(feature.getPositiveInt("autoreconnect.session_ttl_seconds", 600)).thenReturn(600);
        when(feature.getPositiveInt("autoreconnect.ready_publish_attempts", 12)).thenReturn(3);
        when(feature.getPositiveInt("autoreconnect.ready_retry_seconds", 5)).thenReturn(1);
        return feature;
    }

    private DurableMessagingDataAccess successfulMessaging() {
        DurableMessagingDataAccess messaging = mock(DurableMessagingDataAccess.class);
        when(messaging.publish(any(), any())).thenReturn(
                CompletableFuture.completedFuture(mock(PublishedDurableEvent.class))
        );
        return messaging;
    }

    private RestartLifecyclePublisher publisher(
            Restart feature,
            DurableMessagingDataAccess messaging,
            RestartMarkerStore store
    ) {
        return new RestartLifecyclePublisher(
                feature,
                messaging,
                store,
                "server.restart.lifecycle",
                "survival"
        );
    }

    private RestartMarkerStore store(String name) {
        return new RestartMarkerStore(temporaryDirectory.resolve(name));
    }

    private RestartMarker marker(String restartId, long now) {
        return new RestartMarker(
                restartId,
                "survival",
                now,
                now + 60_000L,
                5_000L,
                250L
        );
    }
}
