package nl.hauntedmc.serverfeatures.localization;

import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.commonlib.localization.MessageType;
import nl.hauntedmc.commonlib.util.ComponentUtils;
import nl.hauntedmc.commonlib.util.PlaceholderUtils;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import nl.hauntedmc.serverfeatures.api.player.PlayerRegistryAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {
    public static final String LANG_DIR = "lang";

    private final ServerFeatures plugin;
    private final ResourceHandler defaultMessagesResource;
    private final EnumMap<Language, ResourceHandler> languageResources = new EnumMap<>(Language.class);

    public LocalizationHandler(ServerFeatures plugin) {
        this.plugin = plugin;
        this.defaultMessagesResource = new ResourceHandler(plugin, LANG_DIR + "/messages.yml");
        loadLanguageFiles();
    }

    /**
     * Loads each language file using ResourceHandler.
     */
    private void loadLanguageFiles() {
        for (Language lang : Language.values()) {
            String resourcePath = LANG_DIR + "/" + lang.getFileName();
            ResourceHandler resource = new ResourceHandler(plugin, resourcePath);
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

    // --- Fluent Builder API ---

    /**
     * Entry point for retrieving a localized message.
     * Example usage:
     *     Component comp = localizationHandler
     *                           .getMessage("welcome.message")
     *                           .forAudience(someAudience)
     *                           .ofType(MessageType.MiniMessage)
     *                           .withPlaceholders(somePlaceholders)
     *                           .build();
     */
    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public class MessageBuilder {
        private final String key;
        private Audience audience;
        private MessageType messageType = MessageType.Legacy;
        private Map<String, String> placeholders;

        private MessageBuilder(String key) {
            this.key = key;
        }

        /**
         * Set the target audience (for example, a Player).
         * If no audience is provided, a system (default) message is assumed.
         */
        public MessageBuilder forAudience(Audience audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Specify the deserialization method: Legacy or MiniMessage.
         * Defaults to MessageType.Legacy.
         */
        public MessageBuilder ofType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        /**
         * Set the placeholders to apply to the message.
         */
        public MessageBuilder withPlaceholders(Map<String, String> placeholders) {
            this.placeholders = placeholders;
            return this;
        }

        /**
         * Build and return the configured message component.
         */
        public Component build() {
            String rawMessage;
            // If audience is a Player, use translated (localized) version.
            if (audience instanceof Player) {
                rawMessage = getTranslateMessage(key, (Player) audience);
            } else {
                // Otherwise, use the default message.
                rawMessage = defaultMessagesResource.getConfig()
                        .getString(key, "&cMessage not found: " + key);
            }
            return parseAndDeserializeToComponent(rawMessage, messageType, placeholders, audience);
        }

        public String buildPlain() {
            String rawMessage;
            // If audience is a Player, use translated (localized) version.
            if (audience instanceof Player) {
                rawMessage = getTranslateMessage(key, (Player) audience);
            } else {
                // Otherwise, use the default message.
                rawMessage = defaultMessagesResource.getConfig()
                        .getString(key, "&cMessage not found: " + key);
            }
            return parseAndDeserializeToString(rawMessage, placeholders, audience);
        }
    }

    // --- Private Helper Methods ---

    /**
     * Retrieves a translated message for a player based on their language.
     * Falls back to the default message if no localized version is found.
     */
    private @NotNull String getTranslateMessage(String key, Player player) {
        Language language = PlayerRegistryAPI.getPlayerLanguage(player);
        String message = null;
        if (language != null) {
            ResourceHandler resource = languageResources.get(language);
            if (resource != null && resource.getConfig().contains(key)) {
                message = resource.getConfig().getString(key);
            }
        }
        if (message == null) {
            message = defaultMessagesResource.getConfig()
                    .getString(key, "&cMessage not found: " + key);
        }
        return message;
    }


    /**
     * Applies placeholder processing, color parsing, and then serializes the message string to a Component.
     * If MessageType is MiniMessage, an alternate serialization is applied.
     */
    private Component parseAndDeserializeToComponent(String message, MessageType messageType,
                                                     Map<String, String> placeholders, Audience audience) {
        if (placeholders != null) {
            message = PlaceholderUtils.parsePlaceholders(message, placeholders);
        }
        if (audience instanceof Player) {
            message = PlaceholderAPIHook.parseWithPAPI(message, (Player) audience);
        }
        message = ComponentUtils.serializeLegacyString(message);
        return (messageType == MessageType.MiniMessage)
                ? ComponentUtils.deserializeMMComponent(message)
                : ComponentUtils.deserializeComponent(message);
    }

    /**
     * Applies placeholder processing, color parsing, and then returns the final message string.
     */
    private String parseAndDeserializeToString(String message,
                                                     Map<String, String> placeholders, Audience audience) {
        if (placeholders != null) {
            message = PlaceholderUtils.parsePlaceholders(message, placeholders);
        }
        if (audience instanceof Player) {
            message = PlaceholderAPIHook.parseWithPAPI(message, (Player) audience);
        }
        message = ComponentUtils.serializeLegacyString(message);
        return message;
    }
}
