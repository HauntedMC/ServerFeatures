package nl.hauntedmc.serverfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.PublishedDurableEvent;
import nl.hauntedmc.proxyfeatures.contracts.messaging.VanishStateMessage;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventBusHandlerTest {

    @Test
    void publishesVersionedDurableEventsWithIncreasingRevisions() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        Vanish feature = feature();
        when(redis.publish(eq("proxy.vanish.update"), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new PublishedDurableEvent("event", "1-0", true)
                ));
        EventBusHandler handler = handler(feature, redis, 3);
        UUID uuid = UUID.randomUUID();

        handler.publishState(uuid.toString(), "Remy", true).join();
        handler.publishState(uuid.toString(), "Remy", false).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<DurableEvent<VanishStateMessage>> events = ArgumentCaptor.forClass(DurableEvent.class);
        verify(redis, times(2)).publish(eq("proxy.vanish.update"), events.capture());
        List<DurableEvent<VanishStateMessage>> published = events.getAllValues();
        assertTrue(published.get(1).payload().getStateVersion()
                > published.get(0).payload().getStateVersion());
        for (DurableEvent<VanishStateMessage> event : published) {
            assertEquals(event.eventId(), event.processingKey());
            assertEquals(event.eventId(), event.payload().getDurableKey());
            assertEquals("survival", event.payload().getServer());
        }
    }

    @Test
    void retryReusesTheSameEventIdentity() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        Vanish feature = feature();
        runDelayedTasksImmediately(feature);
        when(redis.publish(eq("proxy.vanish.update"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")))
                .thenReturn(CompletableFuture.completedFuture(
                        new PublishedDurableEvent("event", "1-0", true)
                ));
        EventBusHandler handler = handler(feature, redis, 3);

        handler.publishState(UUID.randomUUID().toString(), "Remy", true).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<DurableEvent<VanishStateMessage>> events = ArgumentCaptor.forClass(DurableEvent.class);
        verify(redis, times(2)).publish(eq("proxy.vanish.update"), events.capture());
        assertEquals(events.getAllValues().get(0).eventId(), events.getAllValues().get(1).eventId());
        verify(feature.getLogger()).warning(contains("Retrying durable vanish update"));
    }

    @Test
    void exhaustedRetriesCompleteExceptionally() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        Vanish feature = feature();
        runDelayedTasksImmediately(feature);
        when(redis.publish(eq("proxy.vanish.update"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));
        EventBusHandler handler = handler(feature, redis, 2);

        CompletableFuture<PublishedDurableEvent> result = handler.publishState(
                UUID.randomUUID().toString(),
                "Remy",
                true
        );

        assertThrows(RuntimeException.class, result::join);
        verify(redis, times(2)).publish(eq("proxy.vanish.update"), any());
        verify(feature.getLogger()).severe(contains("after 2 attempt(s)"));
    }

    @Test
    void invalidUuidIsRejectedBeforeRedisPublication() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        EventBusHandler handler = handler(feature(), redis, 3);

        CompletableFuture<PublishedDurableEvent> result = handler.publishState(
                "not-a-uuid",
                "Remy",
                true
        );

        assertTrue(result.isCompletedExceptionally());
        verifyNoInteractions(redis);
    }

    @Test
    void constructorRejectsInvalidRetrySettings() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        Vanish feature = feature();

        assertThrows(
                IllegalArgumentException.class,
                () -> new EventBusHandler(
                        feature,
                        redis,
                        "survival",
                        "proxy.vanish.update",
                        0,
                        250L
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventBusHandler(
                        feature,
                        redis,
                        "survival",
                        "proxy.vanish.update",
                        3,
                        0L
                )
        );
    }

    private static EventBusHandler handler(
            Vanish feature,
            DurableMessagingDataAccess redis,
            int attempts
    ) {
        return new EventBusHandler(
                feature,
                redis,
                "survival",
                "proxy.vanish.update",
                attempts,
                250L
        );
    }

    private static Vanish feature() {
        Vanish feature = mock(Vanish.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(lifecycle.getTaskManager()).thenReturn(tasks);
        return feature;
    }

    private static void runDelayedTasksImmediately(Vanish feature) {
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();
        when(tasks.scheduleAsyncDelayedTask(any(Runnable.class), any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return mock(BukkitTask.class);
                });
    }
}
