package nl.hauntedmc.serverfeatures.localization;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.util.TextUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {

    private final ServerFeatures plugin;

    // Default messages configuration and file.
    private FileConfiguration defaultMessagesConfig;
    private File defaultMessagesFile;

    // Map to hold language-specific configurations.
    private final EnumMap<Language, FileConfiguration> languageConfigs = new EnumMap<>(Language.class);

    public LocalizationHandler(ServerFeatures plugin) {
        this.plugin = plugin;
        loadDefaultMessages();
        loadLanguageFiles();
    }

    /**
     * Loads the default messages.yml file.
     */
    private void loadDefaultMessages() {
        defaultMessagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!defaultMessagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        defaultMessagesConfig = YamlConfiguration.loadConfiguration(defaultMessagesFile);
    }

    /**
     * Loads each language file based on the defined enum.
     * If a file does not exist, a warning is logged.
     */
    private void loadLanguageFiles() {
        for (Language lang : Language.values()) {
            File langFile = new File(plugin.getDataFolder(), lang.getFileName());
            if (!langFile.exists()) {
                plugin.getLogger().warning("Language file " + lang.getFileName() + " not found. Please create it manually.");
                continue;
            }
            FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            languageConfigs.put(lang, langConfig);
        }
    }

    /**
     * Reloads both the default and language-specific message files.
     */
    public void reloadLocalization() {
        loadDefaultMessages();
        loadLanguageFiles();
        plugin.getLogger().info("All localization files reloaded.");
    }

    /**
     * Registers multiple default messages in the default file.
     * Only keys not already present will be added.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            String key = entry.getKey();
            String defaultValue = entry.getValue();
            if (!defaultMessagesConfig.contains(key)) {
                defaultMessagesConfig.set(key, defaultValue);
                changes = true;
            }
        }
        if (changes) {
            saveDefaultMessagesFile();
        }
    }

    /**
     * Registers a single default message.
     */
    public void registerDefaultMessage(String key, String defaultValue) {
        if (!defaultMessagesConfig.contains(key)) {
            defaultMessagesConfig.set(key, defaultValue);
            saveDefaultMessagesFile();
        }
    }

    /**
     * Saves changes to the default messages file.
     */
    private void saveDefaultMessagesFile() {
        try {
            defaultMessagesConfig.save(defaultMessagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml: " + e.getMessage());
        }
    }

    public Component getMessage(String key, Audience targetPlayer, Map<String, String> placeholders) {
        if (targetPlayer instanceof Player) {
            return getPlayerMessage(key, (Player) targetPlayer, placeholders);
        } else {
            return getSystemMessage(key, placeholders);
        }
    }

    public Component getMessage(String key, Audience targetPlayer) {
        if (targetPlayer instanceof Player) {
            return getPlayerMessage(key, (Player) targetPlayer, null);
        } else {
            return getSystemMessage(key, null);
        }
    }

    private Component getPlayerMessage(String key, Player targetPlayer, Map<String, String> placeholders) {
        String message = getTranslateMessage(key, targetPlayer);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        if (targetPlayer != null) {
            message = TextUtils.parseWithPAPI(message, targetPlayer);
        }
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }


    public Component getSystemMessage(String key, Map<String, String> placeholders) {
        String message = defaultMessagesConfig.getString(key, "&cMessage not found: " + key);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }


    private @NotNull String getTranslateMessage(String key, Player targetPlayer) {
        Language language = getPlayerLanguage(targetPlayer);

        String message = null;
        if (language != null) {
            FileConfiguration langConfig = languageConfigs.get(language);
            if (langConfig != null && langConfig.contains(key)) {
                message = langConfig.getString(key);
            }
        }
        if (message == null) {
            message = defaultMessagesConfig.getString(key, "&cMessage not found: " + key);
        }
        return message;
    }

    /**
     * Retrieve the player's language.
     *
     * @param player The player whose language should be retrieved.
     * @return The Language, or null if no language is configured.
     */
    private Language getPlayerLanguage(Player player) {
        return Language.NL;
    }
}
