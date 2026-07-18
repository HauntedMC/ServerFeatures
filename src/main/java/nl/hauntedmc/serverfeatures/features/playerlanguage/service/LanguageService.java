package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerLanguageRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
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
    private final PlayerRepository playerRepository;
    private final PlayerLanguageRepository playerLanguageRepository;
    private final ConcurrentMap<UUID, Language> languageCache = new ConcurrentHashMap<>();

    public LanguageService(PlayerLanguage feature, DataRegistry dataRegistry) {
        this.feature = Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(dataRegistry, "dataRegistry");
        this.playerRepository = dataRegistry.getPlayerRepository();
        this.playerLanguageRepository = dataRegistry.getPlayerLanguageRepository();
    }

    public void warm(UUID playerUuid, String usernameHint) {
        PlayerEntity player = resolvePlayer(playerUuid);
        if (player == null || player.getId() == null) {
            languageCache.remove(playerUuid);
            return;
        }

        PlayerLanguageEntity entity = playerLanguageRepository.findByPlayerId(player.getId()).orElse(null);
        if (entity == null) {
            languageCache.remove(playerUuid);
            return;
        }

        Language effective = fromStoredCode(entity.getEffectiveLanguage());
        if (effective == null) {
            effective = fromStoredCode(entity.getLanguage());
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

        PlayerEntity player = resolvePlayer(playerUuid);
        if (player == null || player.getId() == null) {
            return;
        }

        playerLanguageRepository.saveOrUpdate(player.getId(), language.name(), language.name());
        languageCache.put(playerUuid, language);
    }

    private PlayerEntity resolvePlayer(UUID playerUuid) {
        String uuid = playerUuid.toString();
        return playerRepository.getActivePlayer(uuid)
                .or(() -> playerRepository.findByUUID(uuid))
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
