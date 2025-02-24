package nl.hauntedmc.serverfeatures.localization;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.util.TextUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class LocalizationHandler {

    private final ServerFeatures plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    public LocalizationHandler(ServerFeatures plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reloadLocalization() {
        loadMessages();
        plugin.getLogger().info("Localization file reloaded.");
    }

    /**
     * Register multiple default messages in a single pass.
     * - If the key is not present, set the given default value.
     * - If it is present, don't overwrite it.
     * - Save changes once at the end if any new keys were added.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            String key = entry.getKey();
            String defaultValue = entry.getValue();

            if (!messagesConfig.contains(key)) {
                messagesConfig.set(key, defaultValue);
                changes = true;
            }
        }

        if (changes) {
            saveMessagesFile();
        }
    }

    /**
     * Register a single key → default value (for convenience).
     */
    public void registerDefaultMessage(String key, String defaultValue) {
        if (!messagesConfig.contains(key)) {
            messagesConfig.set(key, defaultValue);
            saveMessagesFile();
        }
    }

    private void saveMessagesFile() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml: " + e.getMessage());
        }
    }

    // -- Existing methods --

    public Component getMessage(String key, Player targetPlayer, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key, "&cMessage not found: " + key);

        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }

        if (targetPlayer != null ) {
            message = TextUtils.parseWithPAPI(message, targetPlayer);
        }

        message = TextUtils.parseLegacyColors(message);

        return TextUtils.serializeComponent(message);
    }

    public Component getMessage(String key) {
        return getMessage(key, null, null);
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        return getMessage(key, null, placeholders);
    }

    public Component getMessage(String key, Player targetPlayer) {
        return getMessage(key, targetPlayer, null);
    }
}
