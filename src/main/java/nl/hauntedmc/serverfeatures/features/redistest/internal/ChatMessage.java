package nl.hauntedmc.serverfeatures.features.redistest.internal;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

public final class ChatMessage extends AbstractEventMessage {
    private final String server, player, message;

    // No‑arg ctor for Gson
    @SuppressWarnings("unused")
    private ChatMessage() {
        super("chat");
        this.server  = null;
        this.player  = null;
        this.message = null;
    }

    public ChatMessage(String server, String player, String message) {
        super("chat");
        this.server  = server;
        this.player  = player;
        this.message = message;
    }

    public String getServer()  { return server; }
    public String getPlayer()  { return player; }
    public String getMessage() { return message; }
}