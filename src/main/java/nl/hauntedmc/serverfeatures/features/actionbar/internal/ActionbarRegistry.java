package nl.hauntedmc.serverfeatures.features.actionbar.internal;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.actionbar.Actionbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActionbarRegistry {

    private final Actionbar feature;
    private final List<ActionbarMessage> messages = new ArrayList<>();

    public ActionbarRegistry(Actionbar feature) {
        this.feature = feature;
        loadMessagesFromConfig();
    }

    private void loadMessagesFromConfig() {
        messages.clear();

        ConfigNode root = feature.getConfigHandler().node("messages");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            String id = entry.getKey();
            ConfigNode n = entry.getValue();

            String key = n.get("message_key").as(String.class, id);
            long duration;
            duration = n.get("duration").as(Long.class, 100L);

            ActionbarMessage message = new ActionbarMessage.Builder()
                    .messageKey(key)
                    .duration(duration)
                    .build();
            messages.add(message);
        }
    }

    public List<ActionbarMessage> getMessages() {
        return messages;
    }

    public ActionbarMessage get(int currentMessageIndex) {
        if (messages.isEmpty()) {
            return new ActionbarMessage.Builder().messageKey("default").duration(100).build();
        }

        return messages.get(currentMessageIndex);
    }

    public int getTotalMessages() {
        return messages.size();
    }
}
