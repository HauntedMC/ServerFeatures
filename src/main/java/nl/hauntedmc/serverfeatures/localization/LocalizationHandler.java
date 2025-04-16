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
        this.defaultMessagesResource = new ResourceHandler(plugin, "messages.yml");
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

    // --- Fluent Builder API ---

    /**
     * Entry point for retrieving a localized message.
     *
     * Example usage:
     *
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
            return parseAndSerialize(rawMessage, messageType, placeholders, audience);
        }
    }

    // --- Private Helper Methods ---

    /**
     * Retrieves a translated message for a player based on their language.
     * Falls back to the default message if no localized version is found.
     */
    private @NotNull String getTranslateMessage(String key, Player player) {
        Language language = getPlayerLanguage(player);
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
     * Default method for detecting the player's language.
     * Modify this as needed to reflect a player's actual language.
     */
    private Language getPlayerLanguage(Player player) {
        return Language.NL;
    }

    /**
     * Applies placeholder processing, color parsing, and then serializes the message string to a Component.
     * If MessageType is MiniMessage, an alternate serialization is applied.
     */
    private Component parseAndSerialize(String message, MessageType messageType,
                                        Map<String, String> placeholders, Audience audience) {
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        if (audience instanceof Player) {
            message = TextUtils.parseWithPAPI(message, (Player) audience);
        }
        message = TextUtils.parseLegacyColors(message);
        return (messageType == MessageType.MiniMessage)
                ? TextUtils.deserializeMMComponent(message)
                : TextUtils.deserializeComponent(message);
    }
}
