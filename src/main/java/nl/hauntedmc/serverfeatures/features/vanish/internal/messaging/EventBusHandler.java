package nl.hauntedmc.serverfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;

import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around the messaging bus to publish vanish updates.
 */
public class EventBusHandler {

    private static final String CHANNEL = "proxy.vanish.update";

    private final MessagingDataAccess redisBus;
    private final Vanish feature;
    private final String serverName;

    public EventBusHandler(Vanish feature, MessagingDataAccess redisBus, String serverName) {
        this.feature = feature;
        this.redisBus = redisBus;
        this.serverName = serverName;
    }

    public void publishState(String playerUuid, String playerName, boolean vanished) {
        if (playerUuid == null || playerUuid.isEmpty()) {
            CompletableFuture.completedFuture(null);
            return;
        }
        VanishStateMessage msg = new VanishStateMessage(
                "vanish_update",
                playerUuid,
                playerName != null ? playerName : "",
                vanished,
                serverName != null ? serverName : ""
        );

        redisBus.publish(CHANNEL, msg)
                .exceptionally(ex -> {
                    feature.getLogger().severe("Failed to publish vanish update for " + playerName + " (" + playerUuid + "): " + ex.getMessage());
                    return null;
                });
    }
}
