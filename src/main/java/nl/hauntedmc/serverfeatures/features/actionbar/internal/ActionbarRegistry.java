package nl.hauntedmc.serverfeatures.features.actionbar.internal;

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
        Object raw = feature.getConfigHandler().getSetting("messages");
        if (raw instanceof List<?> messageList) {
            for (Object obj : messageList) {
                if (obj instanceof Map<?, ?> map) {
                    String key = map.get("message_key").toString();

                    long duration;
                    try {
                        duration = Long.parseLong(map.get("duration").toString());
                    } catch (NumberFormatException e) {
                        duration = 100L;
                    }

                    ActionbarMessage message = new ActionbarMessage.Builder()
                            .messageKey(key)
                            .duration(duration)
                            .build();
                    messages.add(message);
                }
            }
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
