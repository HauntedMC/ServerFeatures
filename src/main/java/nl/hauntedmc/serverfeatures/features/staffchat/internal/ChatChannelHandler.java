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
        this.channels = new HashMap<>();

        String staffPrefix = (String) feature.getConfigHandler().getSetting("staff_prefix");
        channels.put(staffPrefix, new ChatChannel("staff", staffPrefix));

        String teamPrefix = (String) feature.getConfigHandler().getSetting("team_prefix");
        channels.put(teamPrefix, new ChatChannel("team", teamPrefix));

        String adminPrefix = (String) feature.getConfigHandler().getSetting("admin_prefix");
        channels.put(adminPrefix, new ChatChannel("admin", adminPrefix));
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

}
