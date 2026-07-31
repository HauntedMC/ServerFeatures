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
        Restart feature = mock(Restart.class);
        when(feature.getLogger()).thenReturn(mock(FeatureLogger.class));
        when(feature.getPositiveInt("autoreconnect.wait_after_ready_seconds", 5)).thenReturn(5);
        when(feature.getPositiveLong("autoreconnect.player_interval_millis", 250L)).thenReturn(250L);
        when(feature.getPositiveInt("autoreconnect.session_ttl_seconds", 600)).thenReturn(600);
        when(feature.getPositiveInt("autoreconnect.ready_publish_attempts", 12)).thenReturn(3);
        when(feature.getPositiveInt("autoreconnect.ready_retry_seconds", 5)).thenReturn(1);

        DurableMessagingDataAccess messaging = mock(DurableMessagingDataAccess.class);
        PublishedDurableEvent published = mock(PublishedDurableEvent.class);
        when(messaging.publish(any(), any())).thenReturn(
                CompletableFuture.completedFuture(published)
        );

        RestartMarkerStore store = new RestartMarkerStore(
                temporaryDirectory.resolve("restart.properties")
        );
        long now = System.currentTimeMillis();
        RestartMarker stale = new RestartMarker(
                "stale-restart",
                "survival",
                now,
                now + 60_000L,
                5_000L,
                250L
        );
        RestartMarker replacement = new RestartMarker(
                "replacement-restart",
                "survival",
                now + 1L,
                now + 60_001L,
                5_000L,
                250L
        );
        store.save(replacement);

        RestartLifecyclePublisher publisher = new RestartLifecyclePublisher(
                feature,
                messaging,
                store,
                "server.restart.lifecycle",
                "survival"
        );
        publisher.publishCancel(stale).join();

        assertEquals(replacement, store.load().orElseThrow());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DurableEvent<RestartLifecycleMessage>> eventCaptor =
                ArgumentCaptor.forClass(DurableEvent.class);
        verify(messaging).publish(eq("server.restart.lifecycle"), eventCaptor.capture());
        assertEquals(
                "stale-restart",
                eventCaptor.getValue().payload().getRestartId()
        );
        assertEquals(
                RestartLifecycleMessage.ACTION_CANCEL,
                eventCaptor.getValue().payload().getAction()
        );
    }
}
