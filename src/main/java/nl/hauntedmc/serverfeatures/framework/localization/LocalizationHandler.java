package nl.hauntedmc.serverfeatures.framework.localization;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServices;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {
    public static final String LANG_DIR = "lang";

    private final ServerFeatures plugin;

    // messages.yml (defaults/fallbacks)
    private final ConfigView defaultMessages;

    // language-specific YAMLs (e.g., lang/nl_NL.yml)
    private final EnumMap<Language, ConfigView> languageViews = new EnumMap<>(Language.class);

    public LocalizationHandler(ServerFeatures plugin) {
        this.plugin = plugin;

        ConfigService service = new ConfigService(plugin);

        // Always ensure default messages file exists (copy from jar if present)
        this.defaultMessages = service.view(LANG_DIR + "/messages.yml", /*copyDefaultsIfPresent*/ true);

        // Load all language views
        loadLanguageFiles(service);
    }

    /**
     * Loads each language file via ConfigService/ConfigView.
     * If the file isn't bundled in the jar, we still create an empty local file
     * (and warn once so admins know to populate it).
     */
    private void loadLanguageFiles(ConfigService service) {
        for (Language lang : Language.values()) {
            String resourcePath = LANG_DIR + "/" + lang.getFileName();

            // Create/open the file; copy defaults if present in the jar
            ConfigView view = service.view(resourcePath, /*copyDefaultsIfPresent*/ true);
            languageViews.put(lang, view);

            // If not bundled in the jar, log a gentle warning (same spirit as before)
            if (plugin.getResource(resourcePath) == null) {
                plugin.getLogger().warning("Language file " + lang.getFileName() +
                        " not found in jar. An empty file is created under " + resourcePath +
                        ". Please populate it to localize messages.");
            }
        }
    }

    /**
     * Reloads both the default messages and language-specific files.
     */
    public void reloadLocalization() {
        defaultMessages.file.reload();
        languageViews.values().forEach(v -> v.file.reload());
        plugin.getLogger().info("All localization files reloaded.");
    }

    /**
     * Registers multiple default messages into lang/messages.yml if absent.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        defaultMessages.batch(b -> {
            for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
                String key = entry.getKey();
                String def = entry.getValue();
                // Only write when missing (keeps admin edits intact)
                if (defaultMessages.get(key) == null) {
                    b.put(key, def);
                }
            }
        });
    }

    // --- Fluent Builder API ---

    /**
     * Entry point for retrieving a localized message.
     * Usage:
     * Component comp = localizationHandler
     *     .getMessage("welcome.message")
     *     .forAudience(player)
     *     .withPlaceholders(MessagePlaceholders.of("player", player.getName()))
     *     .build();
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

        public MessageBuilder with(String key, String value) {
            this.placeholders = MessagePlaceholders.builder().addAll(this.placeholders).addString(key, value).build();
            return this;
        }

        public MessageBuilder with(String key, Number value) {
            this.placeholders = MessagePlaceholders.builder().addAll(this.placeholders).addNumber(key, value).build();
            return this;
        }

        public MessageBuilder with(String key, Component value) {
            this.placeholders = MessagePlaceholders.builder().addAll(this.placeholders).addComponent(key, value).build();
            return this;
        }

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
                    : defaultMessages.get(key, String.class, "&cMessage not found: " + key);

            return renderComponent(rawMessage);
        }

        private Component renderComponent(String messageString) {
            messageString = TextFormatter.convert(messageString)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .preprocess(s -> {
                        if (audience instanceof Player p) {
                            s = PlaceholderAPIHook.applyPlaceholders(s, p);
                        }
                        s = MessagePlaceholders.applyPlaceholders(s, placeholders);
                        return s;
                    })
                    .toMiniMessage();

            ComponentFormatter.Converter converter = ComponentFormatter.deserialize(messageString)
                    .expect(TextFormatter.InputFormat.MINIMESSAGE)
                    .features(ComponentFormatter.ALL_DEFAULTS());

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
        Language language = FeatureServices.find(plugin, LanguageAPI.class)
                .map(api -> api.get(player.getUniqueId()))
                .orElse(Language.NL);

        // Try language-specific file first
        if (language != null) {
            ConfigView view = languageViews.get(language);
            if (view != null) {
                String localized = view.get(key, String.class);
                if (localized != null) {
                    return localized;
                }
            }
        }

        // Fallback to defaults
        return defaultMessages.get(key, String.class, "&cMessage not found: " + key);
    }
}
