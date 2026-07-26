package nl.hauntedmc.serverfeatures.framework.localization;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.serverfeatures.framework.config.ConfigMigrationMerger;
import nl.hauntedmc.serverfeatures.framework.config.FeatureStoragePaths;
import nl.hauntedmc.serverfeatures.framework.service.FeatureServices;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Localization store for either framework messages or one feature's messages.
 */
public final class LocalizationHandler {
    public static final String LANG_DIR = "lang";

    private final ServerFeatures plugin;
    private final Logger logger;
    private final ClassLoader resources;
    private final ConfigService configService;
    private final String featureName;
    private final LocalizationHandler frameworkFallback;
    private final ConfigView defaultMessagesView;
    private final EnumMap<Language, ConfigView> languageViews = new EnumMap<>(Language.class);

    public LocalizationHandler(ServerFeatures plugin, ConfigService configService) {
        this(plugin, plugin.getLogger(), plugin.getClass().getClassLoader(), configService, null, null);
    }

    private LocalizationHandler(
            ServerFeatures plugin,
            Logger logger,
            ClassLoader resources,
            ConfigService configService,
            String featureName,
            LocalizationHandler frameworkFallback
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.featureName = featureName;
        this.frameworkFallback = frameworkFallback;
        this.defaultMessagesView = configService.view(defaultMessagesPath(), featureName == null);
        reloadLocalization();
    }

    public LocalizationHandler openFeatureLocalization(String requestedFeatureName) {
        String normalized = FeatureStoragePaths.normalizeFeatureName(requestedFeatureName);
        if (featureName != null) {
            return frameworkFallback.openFeatureLocalization(normalized);
        }
        return new LocalizationHandler(plugin, logger, resources, configService, normalized, this);
    }

    public void reloadLocalization() {
        if (featureName == null) {
            registerBundledFrameworkDefaults();
        }
        defaultMessagesView.file.reload();
        reloadLanguageViews();
        logger.info(featureName == null
                ? "Framework localization files reloaded."
                : "Localization files reloaded for feature '" + featureName + "'.");
    }

    public void registerDefaultMessages(MessageMap messageMap) {
        if (messageMap == null || messageMap.getMessages().isEmpty()) {
            return;
        }
        Map<String, String> missing = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            if (defaultMessagesView.node(entry.getKey()).isNull()) {
                missing.put(entry.getKey(), entry.getValue());
            }
        }
        if (!missing.isEmpty()) {
            defaultMessagesView.batch(batch -> missing.forEach(batch::put));
            logger.info(featureName == null
                    ? "Registered missing framework localization defaults."
                    : "Registered missing localization defaults for feature '" + featureName + "'.");
        }
    }

    public void migrateLegacyFeatureMessages(MessageMap messageMap) {
        if (featureName == null || frameworkFallback == null
                || messageMap == null || messageMap.getMessages().isEmpty()) {
            return;
        }
        Set<String> ownedRoots = collectOwnedRoots(messageMap);
        moveOwnedRootsFromLegacyStore(frameworkFallback.defaultMessagesView, defaultMessagesView, ownedRoots);
        for (Language language : Language.values()) {
            ConfigView source = frameworkFallback.languageViews.get(language);
            if (source == null || ownedRoots.stream().noneMatch(root -> !source.node(root).isNull())) {
                continue;
            }
            moveOwnedRootsFromLegacyStore(source, languageView(language, true), ownedRoots);
        }
    }

    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public final class MessageBuilder {
        private final String key;
        private Audience audience;
        private MessagePlaceholders placeholders = MessagePlaceholders.empty();
        private boolean autoLinkUrls;
        private boolean autoLinkUnderline = true;

        private MessageBuilder(String key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public MessageBuilder forAudience(Audience audience) {
            this.audience = audience;
            return this;
        }

        public MessageBuilder withPlaceholders(MessagePlaceholders placeholders) {
            if (placeholders != null) {
                this.placeholders = placeholders;
            }
            return this;
        }

        public MessageBuilder with(String key, String value) {
            placeholders = MessagePlaceholders.builder().addAll(placeholders).addString(key, value).build();
            return this;
        }

        public MessageBuilder with(String key, Number value) {
            placeholders = MessagePlaceholders.builder().addAll(placeholders).addNumber(key, value).build();
            return this;
        }

        public MessageBuilder with(String key, Component value) {
            placeholders = MessagePlaceholders.builder().addAll(placeholders).addComponent(key, value).build();
            return this;
        }

        public MessageBuilder autoLinkUrls(boolean enabled) {
            autoLinkUrls = enabled;
            return this;
        }

        public MessageBuilder autoLinkUnderline(boolean enabled) {
            autoLinkUnderline = enabled;
            return this;
        }

        public Component build() {
            String raw = audience instanceof Player player
                    ? resolvePlayerMessage(key, player)
                    : resolveNonPlayerMessage(key);
            return render(raw);
        }

        private Component render(String raw) {
            String message = TextFormatter.convert(raw)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .preprocess(text -> {
                        String replaced = text;
                        if (audience instanceof Player player) {
                            replaced = PlaceholderAPIHook.applyPlaceholders(replaced, player);
                        }
                        return MessagePlaceholders.applyPlaceholders(replaced, placeholders);
                    })
                    .toMiniMessage();

            ComponentFormatter.Converter converter = ComponentFormatter.deserialize(message)
                    .expect(TextFormatter.InputFormat.MINIMESSAGE)
                    .features(ComponentFormatter.ALL_DEFAULTS());
            if (autoLinkUrls) {
                converter.autoLinkUrls(autoLinkUnderline);
            }
            return converter.toComponent();
        }
    }

    private String resolvePlayerMessage(String key, Player player) {
        Language language = FeatureServices.find(plugin, LanguageAPI.class)
                .map(api -> api.get(player.getUniqueId()))
                .orElse(Language.NL);
        String message = readScopedMessage(key, language);
        return message == null ? missingMessage(key) : message;
    }

    private String resolveNonPlayerMessage(String key) {
        String message = readScopedMessage(key, null);
        return message == null ? missingMessage(key) : message;
    }

    private String readScopedMessage(String key, Language language) {
        if (language != null) {
            ConfigView languageView = languageViews.get(language);
            if (languageView != null) {
                String translated = languageView.get(key, String.class);
                if (translated != null) {
                    return translated;
                }
            }
        }
        String defaultMessage = defaultMessagesView.get(key, String.class);
        if (defaultMessage != null) {
            return defaultMessage;
        }
        return frameworkFallback == null ? null : frameworkFallback.readScopedMessage(key, language);
    }

    private String missingMessage(String key) {
        return frameworkFallback == null ? "&cMessage not found: " + key : frameworkFallback.missingMessage(key);
    }

    private void moveOwnedRootsFromLegacyStore(ConfigView source, ConfigView target, Set<String> ownedRoots) {
        Map<String, Object> legacyValues = new LinkedHashMap<>();
        for (String root : ownedRoots) {
            ConfigNode sourceNode = source.node(root);
            if (!sourceNode.isNull()) {
                legacyValues.put(root, sourceNode.raw());
            }
        }
        if (!legacyValues.isEmpty()) {
            ConfigMigrationMerger.mergeMissing(target, legacyValues);
            source.batch(batch -> legacyValues.keySet().forEach(batch::remove));
            logger.info("[ServerFeatures] [Localization] Migrated legacy message overrides for feature '"
                    + featureName + "' to '" + defaultMessagesPath() + "'");
        }
    }

    private void registerBundledFrameworkDefaults() {
        mergeBundledDefaultsInto(defaultMessagesView, defaultMessagesPath());
        for (Language language : Language.values()) {
            String path = languagePath(language);
            mergeBundledDefaultsInto(configService.view(path, true), path);
        }
    }

    private void mergeBundledDefaultsInto(ConfigView target, String resourcePath) {
        ConfigNode bundled = loadBundledResource(resourcePath);
        if (bundled != null && !bundled.isNull()) {
            ConfigMigrationMerger.mergeMissing(target, bundled.raw());
        }
    }

    private ConfigNode loadBundledResource(String resourcePath) {
        try (InputStream input = resources.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );
            return ConfigNode.ofRaw(yaml, resourcePath);
        } catch (Exception exception) {
            logger.warning("Could not load bundled localization resource '" + resourcePath
                    + "': " + exception.getMessage());
            return null;
        }
    }

    private Set<String> collectOwnedRoots(MessageMap messageMap) {
        Set<String> roots = new LinkedHashSet<>();
        for (String key : messageMap.getMessages().keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            int dot = key.indexOf('.');
            roots.add(dot >= 0 ? key.substring(0, dot) : key);
        }
        return roots;
    }

    private void reloadLanguageViews() {
        languageViews.clear();
        for (Language language : Language.values()) {
            String path = languagePath(language);
            if (featureName == null) {
                ConfigView view = configService.view(path, true);
                view.file.reload();
                languageViews.put(language, view);
            } else {
                configService.openExisting(path).ifPresent(file -> {
                    file.reload();
                    languageViews.put(language, new ConfigView(file, ""));
                });
            }
        }
    }

    private ConfigView languageView(Language language, boolean createIfMissing) {
        ConfigView existing = languageViews.get(language);
        if (existing != null || !createIfMissing) {
            return existing;
        }
        ConfigView created = configService.view(languagePath(language), false);
        languageViews.put(language, created);
        return created;
    }

    private String defaultMessagesPath() {
        return featureName == null
                ? LANG_DIR + "/messages.yml"
                : FeatureStoragePaths.messagesPath(featureName);
    }

    private String languagePath(Language language) {
        return featureName == null
                ? LANG_DIR + "/" + language.getFileName()
                : FeatureStoragePaths.messagesPath(featureName, language);
    }
}
