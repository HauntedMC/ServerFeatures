package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.contracts.messaging.CommandRelayMessage;
import nl.hauntedmc.serverfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventBusHandlerTest {

    @Test
    void invalidEmptyAndForbiddenMessagesAreIgnored() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<CommandRelayMessage>> consumer = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(
                eq("survival.commandrelay.command"),
                eq(CommandRelayMessage.TYPE),
                eq(CommandRelayMessage.class),
                consumer.capture()
        )).thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("survival.commandrelay.command");
        consumer.getValue().accept(new CommandRelayMessage(null, "proxy"));
        consumer.getValue().accept(new CommandRelayMessage("say hello", null));
        consumer.getValue().accept(new CommandRelayMessage("/", "proxy"));
        consumer.getValue().accept(new CommandRelayMessage("/stop now", "proxy"));

        verify(tasks, never()).scheduleOneTimeTask(any(Runnable.class));
        verify(feature.getLogger()).warning(contains("forbidden"));
    }

    @Test
    void subscribeFailureIsLogged() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.subscribe(any(), any(), eq(CommandRelayMessage.class), any()))
                .thenThrow(new IllegalStateException("down"));

        new EventBusHandler(feature, redis).subscribe("survival.commandrelay.command");

        verify(feature.getLogger()).severe(contains("failed to subscribe"));
    }

    @Test
    void publishUsesSharedContractAndReportsAsyncFailure() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.publish(eq("proxy.commandrelay.command"), any(CommandRelayMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

        new EventBusHandler(feature, redis).publish("proxy.commandrelay.command", "/say hello");

        ArgumentCaptor<CommandRelayMessage> message = ArgumentCaptor.forClass(CommandRelayMessage.class);
        verify(redis).publish(eq("proxy.commandrelay.command"), message.capture());
        assertEquals(CommandRelayMessage.TYPE, message.getValue().getType());
        assertEquals("/say hello", message.getValue().getCommand());
        assertEquals("survival", message.getValue().getOriginServer());
        verify(feature.getLogger()).severe(contains("failed to publish"));
    }

    @Test
    void disableUnsubscribesAtMostOnce() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        Subscription subscription = mock(Subscription.class);
        when(subscription.unsubscribe()).thenReturn(CompletableFuture.completedFuture(null));
        when(redis.subscribe(any(), any(), eq(CommandRelayMessage.class), any())).thenReturn(subscription);

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("survival.commandrelay.command");
        handler.disable();
        handler.disable();

        verify(subscription).unsubscribe();
    }

    private static CommandRelay featureWithWhitelist(List<String> whitelist) {
        CommandRelay feature = mock(CommandRelay.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);

        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(lifecycle.getTaskManager()).thenReturn(tasks);
        when(config.get("command_whitelist")).thenReturn(whitelist);
        when(config.getGlobalSetting("server_name")).thenReturn("survival");
        return feature;
    }
}
