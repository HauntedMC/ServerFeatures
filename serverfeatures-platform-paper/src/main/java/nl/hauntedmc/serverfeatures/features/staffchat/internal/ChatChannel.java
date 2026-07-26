package nl.hauntedmc.serverfeatures.features.staffchat.internal;

/**
 * Represents a chat channel. Each channel manages its own viewers and handles message broadcasting.
 */
public class ChatChannel {

    private final String id;
    private final String permission;
    private final String prefix;

    public ChatChannel(String id, String prefix) {
        this.id = id;
        this.prefix = prefix;
        this.permission = "proxyfeatures.feature.staffchat." + id;
    }

    public String getId() {
        return id;
    }

    public String getPermission() {
        return permission;
    }

    public String getPrefix() {
        return prefix;
    }
}
