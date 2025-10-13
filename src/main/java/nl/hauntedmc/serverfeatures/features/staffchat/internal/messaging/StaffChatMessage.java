package nl.hauntedmc.serverfeatures.features.staffchat.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

public final class StaffChatMessage extends AbstractEventMessage {
    private final String prefix;
    private final String message;
    private final String senderName;
    private final String senderServer;

    // No‑arg ctor for Gson
    @SuppressWarnings("unused")
    private StaffChatMessage() {
        super("staffchat");
        this.prefix = null;
        this.message = null;
        this.senderName = null;
        this.senderServer = null;
    }

    public StaffChatMessage(String type,
                            String prefix,
                            String message,
                            String senderName,
                            String senderServer) {
        super(type);
        this.prefix = prefix;
        this.message = message;
        this.senderName = senderName;
        this.senderServer = senderServer;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getMessage() {
        return message;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderServer() {
        return senderServer;
    }
}
