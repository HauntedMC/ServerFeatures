package nl.hauntedmc.serverfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableDelivery;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableSubscription;
import nl.hauntedmc.proxyfeatures.contracts.messaging.VoteMessage;
import nl.hauntedmc.serverfeatures.features.votifier.Votifier;
import nl.hauntedmc.serverfeatures.features.votifier.event.VoteEvent;
import nl.hauntedmc.serverfeatures.features.votifier.event.VotePayload;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EventBusHandler {

    private static final long CLOSE_TIMEOUT_SECONDS = 5L;

    private final DurableMessagingDataAccess redisBus;
    private final Votifier feature;
    private DurableSubscription subscription;

    public EventBusHandler(Votifier feature, DurableMessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    public void consume(String stream, String consumerGroup) {
        String consumer = consumerGroup + "." + UUID.randomUUID();
        try {
            this.subscription = redisBus.consume(
                    stream,
                    consumerGroup,
                    consumer,
                    VoteMessage.TYPE,
                    VoteMessage.class,
                    this::handleIncoming
            );
            this.subscription.completion().whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    feature.getLogger().severe(
                            "Durable vote consumer stopped unexpectedly: " + rootMessage(throwable)
                    );
                }
            });
            feature.getLogger().info(
                    "Consuming durable vote stream \"" + stream + "\" as group \"" + consumerGroup + "\"."
            );
        } catch (RuntimeException exception) {
            feature.getLogger().severe(
                    "Failed to consume durable vote stream \"" + stream + "\": " + rootMessage(exception)
            );
            throw exception;
        }
    }

    private void handleIncoming(DurableDelivery<VoteMessage> delivery) {
        VoteMessage message = delivery.event().payload();
        if (message.getUsername() == null || message.getServiceName() == null) {
            feature.getLogger().warning(
                    "Discarding invalid durable vote " + delivery.event().processingKey() + "."
            );
            acknowledge(delivery);
            return;
        }

        String processingKey = delivery.event().processingKey();
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                CompletionStage<Void> processing;
                try {
                    processing = dispatchLocalEvent(message, processingKey);
                } catch (Throwable throwable) {
                    feature.getLogger().warning(
                            "Durable vote " + processingKey + " was not dispatched: " + rootMessage(throwable)
                    );
                    return;
                }

                processing.whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        feature.getLogger().warning(
                                "Durable vote " + processingKey + " was not processed: " + rootMessage(throwable)
                        );
                        return;
                    }
                    acknowledge(delivery);
                });
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not schedule durable vote " + processingKey + ": " + rootMessage(exception)
            );
        }
    }

    private CompletionStage<Void> dispatchLocalEvent(VoteMessage message, String processingKey) {
        VoteEvent event = new VoteEvent(new VotePayload(
                message.getServiceName(),
                message.getUsername(),
                message.getAddress() == null ? "-" : message.getAddress(),
                message.getVoteTimestamp(),
                processingKey
        ));
        Bukkit.getPluginManager().callEvent(event);
        return event.processingCompletion();
    }

    private void acknowledge(DurableDelivery<VoteMessage> delivery) {
        delivery.acknowledge().whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                feature.getLogger().warning(
                        "Could not acknowledge durable vote " + delivery.event().processingKey()
                                + ": " + rootMessage(throwable)
                );
            }
        });
    }

    public void disable() {
        DurableSubscription current = subscription;
        subscription = null;
        if (current == null) {
            return;
        }
        try {
            current.closeAsync().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            feature.getLogger().warning("Interrupted while closing the durable vote consumer.");
        } catch (ExecutionException | TimeoutException exception) {
            feature.getLogger().warning(
                    "Could not confirm durable vote consumer shutdown: " + rootMessage(exception)
            );
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
