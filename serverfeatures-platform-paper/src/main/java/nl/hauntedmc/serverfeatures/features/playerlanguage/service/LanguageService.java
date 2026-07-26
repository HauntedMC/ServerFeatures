package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LanguageService implements LanguageAPI {

    private static final Language FALLBACK = Language.EN;

    private final PlayerLanguage feature;
    private final PlayerData players;
    private final ConcurrentMap<UUID, Language> languageCache = new ConcurrentHashMap<>();

    public LanguageService(PlayerLanguage feature, DataRegistryApi dataRegistry) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.players = Objects.requireNonNull(dataRegistry, "dataRegistry").players();
    }

    /**
     * Resolves and caches a player's effective language.
     *
     * <p>The returned stage completes only after the cache has been updated. This allows
     * callers in the asynchronous pre-login phase to ensure localized join UI never uses
     * the fallback language first.</p>
     */
    public java.util.concurrent.CompletionStage<Language> warm(UUID playerUuid) {
        return players.findLanguage(playerUuid).handle((settings, throwable) -> {
            Language effective = null;
            if (throwable == null && settings != null && settings.isPresent()) {
                effective = fromStoredCode(settings.get().effectiveLanguage());
                if (effective == null) {
                    effective = fromStoredCode(settings.get().language());
                }
            }

            if (effective == null) {
                languageCache.remove(playerUuid);
                return FALLBACK;
            }

            languageCache.put(playerUuid, effective);
            return effective;
        });
    }

    public void forget(UUID playerUuid) {
        languageCache.remove(playerUuid);
    }

    @Override
    public Language get(UUID playerUuid) {
        return languageCache.getOrDefault(playerUuid, FALLBACK);
    }

    @Override
    public void set(UUID playerUuid, Language language) {
        setAsync(playerUuid, language);
    }

    public CompletionStage<Boolean> setAsync(UUID playerUuid, Language language) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(language, "language");

        return players.saveLanguage(playerUuid, language.name(), language.name())
                .thenApply(saved -> {
                    if (Boolean.TRUE.equals(saved)) {
                        languageCache.put(playerUuid, language);
                        return true;
                    }
                    return false;
                })
                .exceptionally(throwable -> {
                    feature.getLogger().warning(
                            "Could not save language for " + playerUuid + ": " + rootMessage(throwable)
                    );
                    return false;
                });
    }

    private static Language fromStoredCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        try {
            return Language.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

}
