package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LanguageService implements LanguageAPI {

    private static final Language FALLBACK = Language.EN;

    private final PlayerLanguage feature;
    private final PlayerData players;
    private final ConcurrentMap<UUID, Language> languageCache = new ConcurrentHashMap<>();

    public LanguageService(PlayerLanguage feature, DataRegistry dataRegistry) {
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
        Objects.requireNonNull(language, "language");

        resolvePlayerId(playerUuid).thenAccept(playerId -> playerId.ifPresent(
                value -> players.saveLanguage(value, language.name(), language.name())
        ));
        languageCache.put(playerUuid, language);
    }

    private java.util.concurrent.CompletionStage<Optional<Long>> resolvePlayerId(UUID playerUuid) {
        Optional<PlayerIdentity> cached = players.findActiveIdentityCached(playerUuid);
        if (cached.isPresent()) {
            return java.util.concurrent.CompletableFuture.completedFuture(cached.map(PlayerIdentity::playerId));
        }
        return players.findIdentity(playerUuid).thenApply(identity -> identity.map(PlayerIdentity::playerId));
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

}
