package nl.hauntedmc.serverfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableDelivery;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableSubscription;
import nl.hauntedmc.proxyfeatures.contracts.messaging.VoteMessage;
import nl.hauntedmc.serverfeatures.features.votifier.Votifier;
import nl.hauntedmc.serverfeatures.features.votifier.event.VoteDispatchTracker;
import nl.hauntedmc.serverfeatures.features.votifier.event.VoteEvent;
import nl.hauntedmc.serverfeatures.features.votifier.event.VotePayload;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

    // One-time detection & cached reflection
    private final boolean nativeVotifierAvailable;
    private Constructor<?> voteCtorNoArgs;
    private Constructor<?> eventCtor;
    private Method setServiceName;
    private Method setUsername;
    private Method setAddress;
    private Method setTimeStamp;

    public EventBusHandler(Votifier feature, DurableMessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
        this.nativeVotifierAvailable = detectAndCacheVotifier();
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
        } catch (RuntimeException ex) {
            feature.getLogger().severe(
                    "Failed to consume durable vote stream \"" + stream + "\": " + rootMessage(ex)
            );
            throw ex;
        }
    }

    private void handleIncoming(DurableDelivery<VoteMessage> delivery) {
        VoteMessage msg = delivery.event().payload();
        if (msg.getUsername() == null || msg.getServiceName() == null) {
            feature.getLogger().warning(
                    "Discarding invalid durable vote " + delivery.event().processingKey() + "."
            );
            acknowledge(delivery);
            return;
        }

        final String service = msg.getServiceName();
        final String user = msg.getUsername();
        final String addr = msg.getAddress() == null ? "-" : msg.getAddress();
        final long ts = msg.getVoteTimestamp();
        final String processingKey = delivery.event().processingKey();

        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                CompletionStage<Void> processing;
                try {
                    if (nativeVotifierAvailable) {
                        processing = dispatchTrackedNativeEvent(service, user, addr, ts, processingKey);
                        if (processing == null) {
                            processing = dispatchLocalEvent(service, user, addr, ts, processingKey);
                        }
                    } else {
                        processing = dispatchLocalEvent(service, user, addr, ts, processingKey);
                    }
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

    /**
     * Runs once in the constructor: checks if Votifier is enabled and its API is present.
     * If so, caches reflection members we need to dispatch their event later.
     */
    private boolean detectAndCacheVotifier() {

        if (!Bukkit.getPluginManager().isPluginEnabled("Votifier")) {
            feature.getLogger().info("Votifier not available or incompatible; using native vote events.");
            return false;
        }

        try {
            Class<?> voteCls = Class.forName("com.vexsoftware.votifier.model.Vote", false, getClass().getClassLoader());
            Class<?> eventCls = Class.forName("com.vexsoftware.votifier.model.VotifierEvent", false, getClass().getClassLoader());

            voteCtorNoArgs = voteCls.getConstructor();
            eventCtor = eventCls.getConstructor(voteCls);

            setServiceName = voteCls.getMethod("setServiceName", String.class);
            setUsername = voteCls.getMethod("setUsername", String.class);
            setAddress = voteCls.getMethod("setAddress", String.class);
            setTimeStamp = voteCls.getMethod("setTimeStamp", String.class);

            return true;
        } catch (Throwable t) {
            feature.getLogger().info("Votifier not available or incompatible; using native vote events.");
            return false;
        }
    }

    private boolean dispatchNativeVotifierEvent(String service, String user, String addr, long ts) {
        try {
            Object vote = voteCtorNoArgs.newInstance();
            setServiceName.invoke(vote, service);
            setUsername.invoke(vote, user);
            setAddress.invoke(vote, addr);
            setTimeStamp.invoke(vote, String.valueOf(ts));

            Object event = eventCtor.newInstance(vote);
            Bukkit.getPluginManager().callEvent((Event) event);
            return true;
        } catch (Throwable t) {
            feature.getLogger().severe("Failed to dispatch native Votifier event; falling back. " +
                    t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    private CompletionStage<Void> dispatchTrackedNativeEvent(
            String service,
            String user,
            String addr,
            long ts,
            String processingKey
    ) {
        try (VoteDispatchTracker tracker = VoteDispatchTracker.open(processingKey)) {
            if (!dispatchNativeVotifierEvent(service, user, addr, ts)) {
                return null;
            }
            return tracker.processingCompletion();
        }
    }

    private CompletionStage<Void> dispatchLocalEvent(
            String service,
            String user,
            String addr,
            long ts,
            String processingKey
    ) {
        VoteEvent event = new VoteEvent(new VotePayload(service, user, addr, ts, processingKey));
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
