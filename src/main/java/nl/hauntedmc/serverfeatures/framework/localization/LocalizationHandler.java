package nl.hauntedmc.serverfeatures.framework.localization;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.io.resources.ResourceHandler;
import nl.hauntedmc.serverfeatures.api.player.PlayerRegistryAPI;
import nl.hauntedmc.serverfeatures.api.util.text.ComponentCodec;
import nl.hauntedmc.serverfeatures.api.util.text.MessagePlaceholders;
import nl.hauntedmc.serverfeatures.api.util.text.TextCodec;
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
     * Usage:
     *   Component comp = localizationHandler
     *       .getMessage("welcome.message")
     *       .forAudience(player)
     *       .withPlaceholders(Map.of("player", player.getName()))
     *       .build();
     */
    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public class MessageBuilder {
        private final String key;
        private Audience audience;
        private MessagePlaceholders placeholders = MessagePlaceholders.empty();

        private boolean autoLinkUrls = false;
        private boolean autoLinkUnderline = true;

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
         * Set the placeholders to apply to the message.
         */
        public MessageBuilder withPlaceholders(MessagePlaceholders placeholders) {
            if (placeholders != null) this.placeholders = placeholders;
            return this;
        }

        public MessageBuilder with(String key, String value)        { this.placeholders = MessagePlaceholders.builder().addAll(this.placeholders).addString(key, value).build(); return this; }
        public MessageBuilder with(String key, Number value)        { this.placeholders = MessagePlaceholders.builder().addAll(this.placeholders).addNumber(key, value).build(); return this; }
        public MessageBuilder with(String key, Component value)     { this.placeholders = MessagePlaceholders.builder().addAll(this.placeholders).addComponent(key, value).build(); return this; }

        public MessageBuilder autoLinkUrls(boolean on) {
            this.autoLinkUrls = on;
            return this;
        }

        public MessageBuilder autoLinkUnderline(boolean on) {
            this.autoLinkUnderline = on;
            return this;
        }

        /**
         * Build and return the configured message component.
         * Always uses Hybrid behavior: legacy (&/§, incl. hex) -> MiniMessage tags -> MiniMessage parse.
         */
        public Component build() {
            String rawMessage = (audience instanceof Player player)
                    ? getTranslateMessage(key, player)
                    : defaultMessagesResource.getConfig().getString(key, "&cMessage not found: " + key);

            return renderComponent(rawMessage);
        }

        private Component renderComponent(String messageString) {
            messageString = TextCodec.convert(messageString)
                    .expect(TextCodec.Input.MIXED_INPUT)
                    .preprocess(s -> {
                        if (audience instanceof Player p) {
                            s = PlaceholderAPIHook.applyPlaceholders(s, p);
                        }
                        s = MessagePlaceholders.applyPlaceholders(s, placeholders);
                        return s;
                    })
                    .toMiniMessage();

            ComponentCodec.Converter converter = ComponentCodec.deserialize(messageString)
                    .expect(TextCodec.Input.MINIMESSAGE)
                    .features(ComponentCodec.ALL_DEFAULTS());

            if (autoLinkUrls) {
                converter.autoLinkUrls(autoLinkUnderline);
            }

            return converter.toComponent();
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
            message = defaultMessagesResource.getConfig().getString(key, "&cMessage not found: " + key);
        }
        return message;
    }


}
