package nl.hauntedmc.serverfeatures.localization;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import nl.hauntedmc.serverfeatures.common.util.TextUtils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {

    private final ServerFeatures plugin;

    private final ResourceHandler defaultMessagesResource;
    private final EnumMap<Language, ResourceHandler> languageResources = new EnumMap<>(Language.class);

    public LocalizationHandler(ServerFeatures plugin) {
        this.plugin = plugin;
        defaultMessagesResource = new ResourceHandler(plugin, "messages.yml");
        loadLanguageFiles();
    }

    /**
     * Loads each language file using ResourceHandler.
     */
    private void loadLanguageFiles() {
        for (Language lang : Language.values()) {
            ResourceHandler resource = new ResourceHandler(plugin, lang.getFileName());
            if (resource.getConfig() == null) {
                plugin.getLogger().warning("Language file " + lang.getFileName() + " not found. Please create it manually.");
                continue;
            }
            languageResources.put(lang, resource);
        }
    }

    /**
     * Reloads both the default messages and language-specific files.
     */
    public void reloadLocalization() {
        defaultMessagesResource.reload();
        languageResources.values().forEach(ResourceHandler::reload);
        plugin.getLogger().info("All localization files reloaded.");
    }

    /**
     * Registers multiple default messages.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        FileConfiguration config = defaultMessagesResource.getConfig();
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            String key = entry.getKey();
            String defaultValue = entry.getValue();
            if (!config.contains(key)) {
                config.set(key, defaultValue);
                changes = true;
            }
        }
        if (changes) {
            defaultMessagesResource.save();
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
        String message = defaultMessagesResource.getConfig().getString(key, "&cMessage not found: " + key);
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
            ResourceHandler resource = languageResources.get(language);
            if (resource != null && resource.getConfig().contains(key)) {
                message = resource.getConfig().getString(key);
            }
        }
        if (message == null) {
            message = defaultMessagesResource.getConfig().getString(key, "&cMessage not found: " + key);
        }
        return message;
    }

    /**
     * Retrieve the player's language.
     * Modify this as needed to get the correct language for your player.
     */
    private Language getPlayerLanguage(Player player) {
        return Language.NL;
    }
}
