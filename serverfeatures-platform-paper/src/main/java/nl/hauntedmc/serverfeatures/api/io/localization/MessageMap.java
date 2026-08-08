package nl.hauntedmc.serverfeatures.api.io.localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple holder for multiple (key → default value) message entries.
 */
public class MessageMap {

    private final Map<String, String> messages = new HashMap<>();

    /**
     * Add a single key → default value entry.
     */
    public void add(String key, String defaultValue) {
        messages.put(key, defaultValue);
    }

    /**
     * Returns all message entries.
     */
    public Map<String, String> getMessages() {
        return messages;
    }
}
