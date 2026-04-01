package nl.hauntedmc.serverfeatures.features.staffchat.internal;


import nl.hauntedmc.serverfeatures.features.staffchat.StaffChat;

import java.util.HashMap;
import java.util.Map;

/**
 * Acts as a registry for all chat channels.
 */
public class ChatChannelHandler {

    private final Map<String, ChatChannel> channels;

    public ChatChannelHandler(StaffChat feature) {
        this(
                "staff",
                (String) feature.getConfigHandler().get("staff_prefix"),
                "team",
                (String) feature.getConfigHandler().get("team_prefix"),
                "admin",
                (String) feature.getConfigHandler().get("admin_prefix")
        );
    }

    ChatChannelHandler(String id1, String prefix1, String id2, String prefix2, String id3, String prefix3) {
        this.channels = new HashMap<>();
        putChannel(id1, prefix1);
        putChannel(id2, prefix2);
        putChannel(id3, prefix3);
    }

    /**
     * Retrieves a channel by its prefix.
     *
     * @param prefix the channel prefix
     * @return the corresponding ChatChannel or null if not found
     */
    public ChatChannel getChannelByPrefix(String prefix) {
        return channels.get(prefix);
    }

    private void putChannel(String id, String prefix) {
        if (id == null || id.isBlank() || prefix == null || prefix.isBlank()) return;
        channels.put(prefix, new ChatChannel(id, prefix));
    }

}
