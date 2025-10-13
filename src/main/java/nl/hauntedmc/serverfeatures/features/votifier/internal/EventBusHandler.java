package nl.hauntedmc.serverfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.votifier.Votifier;
import nl.hauntedmc.serverfeatures.features.votifier.event.VoteEvent;
import nl.hauntedmc.serverfeatures.features.votifier.event.VotePayload;
import nl.hauntedmc.serverfeatures.features.votifier.messaging.VoteMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final Votifier feature;
    private Subscription subscription;

    // One-time detection & cached reflection
    private final boolean nativeVotifierAvailable;
    private Constructor<?> voteCtorNoArgs;
    private Constructor<?> eventCtor;
    private Method setServiceName;
    private Method setUsername;
    private Method setAddress;
    private Method setTimeStamp;

    public EventBusHandler(Votifier feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
        this.nativeVotifierAvailable = detectAndCacheVotifier();
    }

    public void subscribe(String channel) {
        try {
            this.subscription = redisBus.subscribe(channel, VoteMessage.class, this::handleIncoming);
            feature.getLogger().info(
                    "Subscribed to channel \"" + channel + "\"."
            );
        } catch (Exception ex) {
            feature.getLogger().severe("Failed to subscribe to \"" + channel + "\"");
        }
    }

    private void handleIncoming(VoteMessage msg) {
        if (msg == null || msg.getUsername() == null || msg.getServiceName() == null) return;

        final String service = msg.getServiceName();
        final String user = msg.getUsername();
        final String addr = msg.getAddress() == null ? "-" : msg.getAddress();
        final long ts = msg.getVoteTimestamp();

        // ensure main thread
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
            if (nativeVotifierAvailable) {
                if (!dispatchNativeVotifierEvent(service, user, addr, ts)) {
                    // Fallback if dispatch fails unexpectedly
                    dispatchLocalEvent(service, user, addr, ts);
                }
            } else {
                dispatchLocalEvent(service, user, addr, ts);
            }
        });
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

    private void dispatchLocalEvent(String service, String user, String addr, long ts) {
        VoteEvent event = new VoteEvent(new VotePayload(service, user, addr, ts));
        Bukkit.getPluginManager().callEvent(event);
    }

    public void disable() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }
}
