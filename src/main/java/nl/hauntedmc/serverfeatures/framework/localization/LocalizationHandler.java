package nl.hauntedmc.serverfeatures.framework.localization;

import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.commonlib.util.ComponentUtils;
import nl.hauntedmc.commonlib.util.PlaceholderUtils;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.player.PlayerRegistryAPI;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.io.resources.ResourceHandler;
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
         * Set the placeholders to apply to the message.
         */
        public MessageBuilder withPlaceholders(Map<String, String> placeholders) {
            this.placeholders = placeholders;
            return this;
        }

        /**
         * Build and return the configured message component.
         * Always uses Hybrid behavior: legacy (&/§, incl. hex) -> MiniMessage tags -> MiniMessage parse.
         */
        public Component build() {
            String rawMessage = (audience instanceof Player)
                    ? getTranslateMessage(key, (Player) audience)
                    : defaultMessagesResource.getConfig().getString(key, "&cMessage not found: " + key);

            return parseToComponent(rawMessage, placeholders, audience);
        }

        /**
         * Build and return a plain string form (primarily for logs/console).
         * Keeps existing legacy serialization behavior after placeholder processing.
         */
        public String buildPlain() {
            String rawMessage = (audience instanceof Player)
                    ? getTranslateMessage(key, (Player) audience)
                    : defaultMessagesResource.getConfig().getString(key, "&cMessage not found: " + key);

            return parseToPlainString(rawMessage, placeholders, audience);
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

    /**
     * Applies placeholders + PAPI, converts legacy to MiniMessage tags, then parses as MiniMessage Component.
     */
    private Component parseToComponent(String message,
                                       Map<String, String> placeholders,
                                       Audience audience) {
        if (placeholders != null) {
            message = PlaceholderUtils.parsePlaceholders(message, placeholders);
        }
        if (audience instanceof Player) {
            message = PlaceholderAPIHook.parseWithPAPI(message, (Player) audience);
        }
        String mmReady = legacyToMiniMessage(message);
        return ComponentUtils.deserializeMMComponent(mmReady);
    }

    /**
     * Applies placeholders + PAPI, then returns a legacy-serialized string.
     * (Useful for logging; not parsed by MiniMessage.)
     */
    private String parseToPlainString(String message,
                                      Map<String, String> placeholders,
                                      Audience audience) {
        if (placeholders != null) {
            message = PlaceholderUtils.parsePlaceholders(message, placeholders);
        }
        if (audience instanceof Player) {
            message = PlaceholderAPIHook.parseWithPAPI(message, (Player) audience);
        }
        // Preserve legacy look for plain text output
        return ComponentUtils.serializeLegacyString(message);
    }

    /**
     * Convert legacy color/format codes (both & and §), including Spigot hex (&x&R&RG&G&B&B) and &#RRGGBB,
     * into MiniMessage tags so strings can mix legacy and MiniMessage safely.
     */
    private static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return input;

        // Normalize § to &
        input = input.replace('§', '&');

        // Hex color formats:
        // 1) "&#RRGGBB"  -> "<#RRGGBB>"
        input = input.replaceAll("(?i)&#([0-9a-f]{6})", "<#$1>");

        // 2) "&x&R&R&G&G&B&B" (Spigot-style) -> "<#RRGGBB>"
        input = input.replaceAll(
                "(?i)&x&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])",
                "<#$1$2$3$4$5$6>"
        );

        // Standard color codes
        input = input
                .replaceAll("(?i)&0", "<black>")
                .replaceAll("(?i)&1", "<dark_blue>")
                .replaceAll("(?i)&2", "<dark_green>")
                .replaceAll("(?i)&3", "<dark_aqua>")
                .replaceAll("(?i)&4", "<dark_red>")
                .replaceAll("(?i)&5", "<dark_purple>")
                .replaceAll("(?i)&6", "<gold>")
                .replaceAll("(?i)&7", "<gray>")
                .replaceAll("(?i)&8", "<dark_gray>")
                .replaceAll("(?i)&9", "<blue>")
                .replaceAll("(?i)&a", "<green>")
                .replaceAll("(?i)&b", "<aqua>")
                .replaceAll("(?i)&c", "<red>")
                .replaceAll("(?i)&d", "<light_purple>")
                .replaceAll("(?i)&e", "<yellow>")
                .replaceAll("(?i)&f", "<white>");

        // Formatting codes
        input = input
                .replaceAll("(?i)&l", "<bold>")
                .replaceAll("(?i)&n", "<underlined>")
                .replaceAll("(?i)&m", "<strikethrough>")
                .replaceAll("(?i)&o", "<italic>")
                .replaceAll("(?i)&k", "<obfuscated>")
                .replaceAll("(?i)&r", "<reset>");

        return input;
    }
}
