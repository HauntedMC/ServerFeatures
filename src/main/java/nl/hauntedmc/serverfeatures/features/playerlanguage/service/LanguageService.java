package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageSettings;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;

import java.util.Locale;
import java.util.Objects;
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

    public void warm(UUID playerUuid) {
        Long playerId = resolvePlayerId(playerUuid);
        if (playerId == null) {
            languageCache.remove(playerUuid);
            return;
        }

        PlayerLanguageSettings settings = players.findLanguage(playerId).orElse(null);
        if (settings == null) {
            languageCache.remove(playerUuid);
            return;
        }

        Language effective = fromStoredCode(settings.effectiveLanguage());
        if (effective == null) {
            effective = fromStoredCode(settings.language());
        }

        if (effective != null) {
            languageCache.put(playerUuid, effective);
        } else {
            languageCache.remove(playerUuid);
        }
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

        Long playerId = resolvePlayerId(playerUuid);
        if (playerId == null) {
            return;
        }

        players.saveLanguage(playerId, language.name(), language.name());
        languageCache.put(playerUuid, language);
    }

    private Long resolvePlayerId(UUID playerUuid) {
        return players.activeIdentity(playerUuid)
                .or(() -> players.findIdentity(playerUuid))
                .map(PlayerIdentity::playerId)
                .orElse(null);
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
