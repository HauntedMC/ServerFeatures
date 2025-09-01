package nl.hauntedmc.serverfeatures.features.votifier.internal;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.votifier.Votifier;
import nl.hauntedmc.serverfeatures.features.votifier.messaging.VoteMessage;
import org.bukkit.Bukkit;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final Votifier feature;
    private Subscription subscription;

    public EventBusHandler(Votifier feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    public void subscribe(String channel) {
        try {
            this.subscription = redisBus.subscribe(channel, VoteMessage.class, this::handleIncoming);
            feature.getLogger().info("Subscribed to channel “" + channel + "”.");
        } catch (Exception ex) {
            feature.getLogger().severe("Failed to subscribe to “" + channel + "”");
        }
    }

    private void handleIncoming(VoteMessage msg) {
        if (msg == null || msg.getUsername() == null || msg.getServiceName() == null) return;

        Vote vote = new Vote();
        vote.setServiceName(msg.getServiceName());
        vote.setUsername(msg.getUsername());
        vote.setAddress(msg.getAddress() == null ? "-" : msg.getAddress());
        vote.setTimeStamp(String.valueOf(msg.getVoteTimestamp()));

        // ensure main thread
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() ->
                Bukkit.getPluginManager().callEvent(new VotifierEvent(vote))
        );
    }

    public void disable() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }
}
