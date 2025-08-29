package nl.hauntedmc.serverfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

/**
 * Message published to Redis whenever a player's vanish state changes
 * or when they join already in a persisted vanished state.
 */
public final class VanishStateMessage extends AbstractEventMessage {

    private final String playerUuid;
    private final String playerName;
    private final boolean vanished;
    private final String server; // origin server name (optional, may be empty)

    // Required no-arg ctor for Gson
    @SuppressWarnings("unused")
    private VanishStateMessage() {
        super("vanish_update");
        this.playerUuid = null;
        this.playerName = null;
        this.vanished   = false;
        this.server     = null;
    }

    public VanishStateMessage(String type,
                              String playerUuid,
                              String playerName,
                              boolean vanished,
                              String server) {
        super(type);
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.vanished   = vanished;
        this.server     = server;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isVanished() {
        return vanished;
    }

    public String getServer() {
        return server;
    }
}
