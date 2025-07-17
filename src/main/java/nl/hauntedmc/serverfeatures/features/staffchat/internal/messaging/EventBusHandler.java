package nl.hauntedmc.serverfeatures.features.staffchat.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.features.staffchat.StaffChat;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final StaffChat feature;


    public EventBusHandler(StaffChat feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    public void publishMessage(StaffChatMessage chatMessage, String channel) {
        redisBus.publish(channel , chatMessage)
                    .exceptionally(ex -> {
                        feature.getLogger().severe("Failed to publish staffchat message.");
                        return null;
                    });
        }
}
