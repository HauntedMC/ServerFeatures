package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableDelivery;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableSubscription;
import nl.hauntedmc.proxyfeatures.contracts.messaging.CommandRelayMessage;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.serverfeatures.api.io.cache.FileCacheStore;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.features.commandrelay.audit.CommandRelayAuditLogService;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventBusHandlerTest {

    private static final String STREAM = "survival.commandrelay.command";
    private static final String CONSUMER_GROUP = "serverfeatures.commandrelay.survival";

    @Test
    void validDeliveryDispatchesThenPersistsAcknowledgesAndAudits() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        runTasksImmediately(feature);
        FileCacheStore store = emptyStore();
        Consumer<DurableDelivery<CommandRelayMessage>> consumer = installConsumer(redis);
        EventBusHandler handler = handler(feature, redis, store);

        Server server = mock(Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(console, "say hello")).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            handler.consume(STREAM, CONSUMER_GROUP);

            CommandRelayMessage message = new CommandRelayMessage(
                    "/say hello",
                    "proxy",
                    "command.operation-1"
            );
            DurableDelivery<CommandRelayMessage> delivery = delivery(message, message.getOperationId());
            consumer.accept(delivery);

            verify(server).dispatchCommand(console, "say hello");
            verify(store).put(eq(message.getOperationId()), any());
            verify(delivery).acknowledge();
            verify(feature.getAuditLogService()).logEvent(
                    "executed",
                    STREAM,
                    "proxy",
                    "say",
                    "say hello",
                    null
            );
        }
    }

    @Test
    void malformedAndForbiddenDeliveriesAreAcknowledgedAndAuditedWithoutScheduling() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        Consumer<DurableDelivery<CommandRelayMessage>> consumer = installConsumer(redis);
        EventBusHandler handler = handler(feature, redis, emptyStore());
        handler.consume(STREAM, CONSUMER_GROUP);

        CommandRelayMessage mismatched = new CommandRelayMessage(
                "/say hello",
                "proxy",
                "command.payload"
        );
        DurableDelivery<CommandRelayMessage> mismatchedDelivery = delivery(
                mismatched,
                "command.envelope"
        );
        consumer.accept(mismatchedDelivery);

        CommandRelayMessage forbidden = new CommandRelayMessage(
                "/stop now",
                "proxy",
                "command.forbidden"
        );
        DurableDelivery<CommandRelayMessage> forbiddenDelivery = delivery(
                forbidden,
                forbidden.getOperationId()
        );
        consumer.accept(forbiddenDelivery);

        CommandRelayMessage missingAlias = new CommandRelayMessage(
                "/   ",
                "proxy",
                "command.missing-alias"
        );
        DurableDelivery<CommandRelayMessage> missingAliasDelivery = delivery(
                missingAlias,
                missingAlias.getOperationId()
        );
        consumer.accept(missingAliasDelivery);

        verify(mismatchedDelivery).acknowledge();
        verify(forbiddenDelivery).acknowledge();
        verify(missingAliasDelivery).acknowledge();
        verify(feature.getLifecycleManager().getTaskManager(), never())
                .scheduleOneTimeTask(any(Runnable.class));
        verify(feature.getLogger()).warning(contains("forbidden"));
        verify(feature.getAuditLogService()).logEvent(
                "invalid_payload",
                STREAM,
                "proxy",
                null,
                "/say hello",
                "operation_id_mismatch"
        );
        verify(feature.getAuditLogService()).logEvent(
                "forbidden_command",
                STREAM,
                "proxy",
                "stop",
                "stop now",
                null
        );
        verify(feature.getAuditLogService()).logEvent(
                "invalid_payload",
                STREAM,
                "proxy",
                null,
                null,
                "missing=command_alias"
        );
    }

    @Test
    void completedReplayIsAcknowledgedAndAuditedWithoutDispatch() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        FileCacheStore store = mock(FileCacheStore.class);
        when(store.listAll()).thenReturn(Map.of(
                "command.done",
                CacheValue.builder(60_000L).with("processed", true).build()
        ));
        Consumer<DurableDelivery<CommandRelayMessage>> consumer = installConsumer(redis);
        EventBusHandler handler = handler(feature, redis, store);
        handler.consume(STREAM, CONSUMER_GROUP);

        CommandRelayMessage message = new CommandRelayMessage("/say hello", "proxy", "command.done");
        DurableDelivery<CommandRelayMessage> delivery = delivery(message, "command.done");
        consumer.accept(delivery);

        verify(delivery).acknowledge();
        verify(feature.getLifecycleManager().getTaskManager(), never())
                .scheduleOneTimeTask(any(Runnable.class));
        verify(feature.getAuditLogService()).logEvent(
                "replay_ignored",
                STREAM,
                "proxy",
                null,
                "/say hello",
                "operation_id=command.done"
        );
    }

    @Test
    void rejectedDispatchIsPersistedAcknowledgedAndAudited() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        runTasksImmediately(feature);
        FileCacheStore store = emptyStore();
        Consumer<DurableDelivery<CommandRelayMessage>> consumer = installConsumer(redis);
        EventBusHandler handler = handler(feature, redis, store);

        Server server = mock(Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(console, "say hello")).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            handler.consume(STREAM, CONSUMER_GROUP);

            CommandRelayMessage message = new CommandRelayMessage(
                    "/say hello",
                    "proxy",
                    "command.rejected"
            );
            DurableDelivery<CommandRelayMessage> delivery = delivery(message, message.getOperationId());
            consumer.accept(delivery);

            verify(store).put(eq(message.getOperationId()), any());
            verify(delivery).acknowledge();
            verify(feature.getAuditLogService()).logEvent(
                    "dispatch_rejected",
                    STREAM,
                    "proxy",
                    "say",
                    "say hello",
                    "success=false"
            );
        }
    }

    @Test
    void dispatchFailureRemainsUnacknowledgedForRetryAndIsAudited() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        runTasksImmediately(feature);
        Consumer<DurableDelivery<CommandRelayMessage>> consumer = installConsumer(redis);
        EventBusHandler handler = handler(feature, redis, emptyStore());

        Server server = mock(Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.dispatchCommand(console, "say hello")).thenThrow(new IllegalStateException("boom"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            handler.consume(STREAM, CONSUMER_GROUP);

            CommandRelayMessage message = new CommandRelayMessage(
                    "/say hello",
                    "proxy",
                    "command.failure"
            );
            DurableDelivery<CommandRelayMessage> delivery = delivery(message, message.getOperationId());
            consumer.accept(delivery);

            verify(delivery, never()).acknowledge();
            verify(feature.getLogger()).warning(contains("dispatch failed"));
            verify(feature.getAuditLogService()).logEvent(
                    "dispatch_error",
                    STREAM,
                    "proxy",
                    "say",
                    "say hello",
                    "boom"
            );
        }
    }

    @Test
    void publishUsesSharedOperationIdAndReportsAsyncFailure() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.publish(eq("proxy.commandrelay.command"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

        CompletableFuture<?> publication = handler(feature, redis, emptyStore())
                .publish("proxy.commandrelay.command", "/say hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<DurableEvent<CommandRelayMessage>> event = ArgumentCaptor.forClass(DurableEvent.class);
        verify(redis).publish(eq("proxy.commandrelay.command"), event.capture());
        assertEquals(CommandRelayMessage.TYPE, event.getValue().payload().getType());
        assertEquals("/say hello", event.getValue().payload().getCommand());
        assertEquals("survival", event.getValue().payload().getOriginServer());
        assertEquals(event.getValue().eventId(), event.getValue().processingKey());
        assertEquals(event.getValue().eventId(), event.getValue().payload().getOperationId());
        assertTrue(publication.isCompletedExceptionally());
        verify(feature.getLogger()).severe(contains("failed to publish"));
    }

    @Test
    void synchronousPublishFailureBecomesFailedFutureAndIsLogged() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.publish(eq("proxy.commandrelay.command"), any()))
                .thenThrow(new IllegalStateException("synchronous down"));

        CompletableFuture<?> publication = assertDoesNotThrow(() ->
                handler(feature, redis, emptyStore())
                        .publish("proxy.commandrelay.command", "/say hello")
        );

        assertTrue(publication.isCompletedExceptionally());
        verify(feature.getLogger()).severe(contains("synchronous down"));
    }

    @Test
    void synchronousAcknowledgementFailureIsContainedAndLogged() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        Consumer<DurableDelivery<CommandRelayMessage>> consumer = installConsumer(redis);
        EventBusHandler handler = handler(feature, redis, emptyStore());
        handler.consume(STREAM, CONSUMER_GROUP);

        CommandRelayMessage message = new CommandRelayMessage(
                "/say hello",
                "proxy",
                "command.payload"
        );
        DurableDelivery<CommandRelayMessage> delivery = delivery(message, "command.envelope");
        when(delivery.acknowledge()).thenThrow(new IllegalStateException("ack down"));

        assertDoesNotThrow(() -> consumer.accept(delivery));
        verify(feature.getLogger()).warning(contains("ack down"));
    }

    @Test
    void disableClosesDurableConsumerAtMostOnce() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        DurableSubscription subscription = subscription();
        when(redis.consume(
                any(),
                any(),
                any(),
                eq(CommandRelayMessage.TYPE),
                eq(CommandRelayMessage.class),
                any()
        )).thenReturn(subscription);

        EventBusHandler handler = handler(feature, redis, emptyStore());
        handler.consume(STREAM, CONSUMER_GROUP);
        handler.disable();
        handler.disable();

        verify(subscription).closeAsync();
    }

    @Test
    void consumeFailureIsLoggedAndPropagated() {
        DurableMessagingDataAccess redis = mock(DurableMessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.consume(
                any(),
                any(),
                any(),
                eq(CommandRelayMessage.TYPE),
                eq(CommandRelayMessage.class),
                any()
        )).thenThrow(new IllegalStateException("down"));

        EventBusHandler handler = handler(feature, redis, emptyStore());
        assertThrows(
                IllegalStateException.class,
                () -> handler.consume(STREAM, CONSUMER_GROUP)
        );

        verify(feature.getLogger()).severe(contains("failed to consume"));
    }

    private static EventBusHandler handler(
            CommandRelay feature,
            DurableMessagingDataAccess redis,
            FileCacheStore store
    ) {
        return new EventBusHandler(
                feature,
                redis,
                new ProcessedCommandLedger(store, 60_000L),
                feature.getAuditLogService()
        );
    }

    private static FileCacheStore emptyStore() {
        FileCacheStore store = mock(FileCacheStore.class);
        when(store.listAll()).thenReturn(Map.of());
        return store;
    }

    private static void runTasksImmediately(CommandRelay feature) {
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();
        when(tasks.scheduleOneTimeTask(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        when(tasks.runAsync(any(Runnable.class))).thenAnswer(invocation -> {
            try {
                invocation.getArgument(0, Runnable.class).run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        });
    }

    private static Consumer<DurableDelivery<CommandRelayMessage>> installConsumer(
            DurableMessagingDataAccess redis
    ) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<DurableDelivery<CommandRelayMessage>>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        DurableSubscription subscription = subscription();
        when(redis.consume(
                eq(STREAM),
                eq(CONSUMER_GROUP),
                anyString(),
                eq(CommandRelayMessage.TYPE),
                eq(CommandRelayMessage.class),
                captor.capture()
        )).thenReturn(subscription);
        return value -> captor.getValue().accept(value);
    }

    private static DurableSubscription subscription() {
        DurableSubscription subscription = mock(DurableSubscription.class);
        when(subscription.completion()).thenReturn(new CompletableFuture<>());
        when(subscription.closeAsync()).thenReturn(CompletableFuture.completedFuture(null));
        return subscription;
    }

    private static DurableDelivery<CommandRelayMessage> delivery(
            CommandRelayMessage message,
            String processingKey
    ) {
        @SuppressWarnings("unchecked")
        DurableDelivery<CommandRelayMessage> delivery = mock(DurableDelivery.class);
        when(delivery.event()).thenReturn(new DurableEvent<>(processingKey, processingKey, message));
        when(delivery.acknowledge()).thenReturn(CompletableFuture.completedFuture(null));
        return delivery;
    }

    private static CommandRelay featureWithWhitelist(List<String> whitelist) {
        CommandRelay feature = mock(CommandRelay.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        CommandRelayAuditLogService auditLogService = mock(CommandRelayAuditLogService.class);

        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(feature.getAuditLogService()).thenReturn(auditLogService);
        when(lifecycle.getTaskManager()).thenReturn(tasks);
        when(config.get("command_whitelist")).thenReturn(whitelist);
        when(config.getGlobalSetting("server_name", String.class, "server")).thenReturn("survival");
        return feature;
    }
}
