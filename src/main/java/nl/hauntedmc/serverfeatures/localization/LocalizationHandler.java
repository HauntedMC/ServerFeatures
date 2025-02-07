package nl.hauntedmc.serverfeatures.localization;

import me.clip.placeholderapi.PlaceholderAPI;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public class LocalizationHandler {

    private final ServerFeatures plugin;
    private FileConfiguration messagesConfig;

    public LocalizationHandler(ServerFeatures plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    /**
     * Loads or creates the messages.yml file.
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    /**
     * Reloads localization messages from file.
     */
    public void reloadLocalization() {
        loadMessages();
        plugin.getLogger().info("Localization file reloaded.");
    }

    /**
     * Gets a formatted message with color codes (& → §) and optional placeholders.
     */
    public String getMessage(String key, Player targetPlayer, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key, "&cMessage not found: " + key);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // Apply PlaceholderAPI placeholders if PAPI is installed & player is provided
        if (targetPlayer != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            message = PlaceholderAPI.setPlaceholders(targetPlayer, message);
        }

        message = ChatColor.translateAlternateColorCodes('&', message); // Convert & to §

        return message;
    }

    /**
     * Gets a simple message (without placeholders).
     */
    public String getMessage(String key) {
        return getMessage(key, null, null);
    }

    /**
     * Gets a message (with own placeholders).
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        return getMessage(key, null, placeholders);
    }

    /**
     * Gets a message (with papi placeholders).
     */
    public String getMessage(String key,  Player targetPlayer) {
        return getMessage(key, targetPlayer, null);
    }
}
