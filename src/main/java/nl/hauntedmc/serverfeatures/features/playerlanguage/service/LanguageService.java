package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LanguageService implements LanguageAPI {

    private static final Language FALLBACK = Language.EN;

    private final ORMContext orm;
    private final ConcurrentMap<UUID, Language> languageCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> playerIdCache = new ConcurrentHashMap<>();

    public LanguageService(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
    }

    public void warm(UUID playerUuid) {
        Language language = orm.runInTransaction(session -> {
            PlayerEntity player = findPlayerEntity(session, playerUuid);
            if (player == null) {
                return null;
            }

            playerIdCache.put(playerUuid, player.getId());
            PlayerLanguageEntity entity = session.get(PlayerLanguageEntity.class, player.getId());
            if (entity == null) {
                return null;
            }

            Language effective = fromStoredCode(entity.getEffectiveLanguage());
            if (effective != null) {
                return effective;
            }

            return fromStoredCode(entity.getLanguage());
        });

        if (language != null) {
            languageCache.put(playerUuid, language);
        } else {
            languageCache.remove(playerUuid);
        }
    }

    public void forget(UUID playerUuid) {
        languageCache.remove(playerUuid);
        playerIdCache.remove(playerUuid);
    }

    @Override
    public Language get(UUID playerUuid) {
        return languageCache.getOrDefault(playerUuid, FALLBACK);
    }

    @Override
    public void set(UUID playerUuid, Language language) {
        Objects.requireNonNull(language, "language");

        boolean persisted = Boolean.TRUE.equals(orm.runInTransaction(session -> {
            PlayerEntity player = findPlayerEntity(session, playerUuid);
            if (player == null) {
                return false;
            }

            PlayerLanguageEntity entity = session.get(PlayerLanguageEntity.class, player.getId());
            if (entity == null) {
                entity = new PlayerLanguageEntity();
                entity.setPlayer(player);
                session.persist(entity);
            }

            entity.setLanguage(language.name());
            entity.setEffectiveLanguage(language.name());
            return true;
        }));

        if (persisted) {
            languageCache.put(playerUuid, language);
        }
    }

    private PlayerEntity findPlayerEntity(org.hibernate.Session session, UUID playerUuid) {
        Long cachedId = playerIdCache.get(playerUuid);
        if (cachedId != null) {
            PlayerEntity cached = session.get(PlayerEntity.class, cachedId);
            if (cached != null) {
                return cached;
            }
            playerIdCache.remove(playerUuid);
        }

        PlayerEntity player = session.createQuery(
                        "FROM PlayerEntity WHERE uuid = :uuid",
                        PlayerEntity.class)
                .setParameter("uuid", playerUuid.toString())
                .setMaxResults(1)
                .uniqueResult();
        if (player != null) {
            playerIdCache.put(playerUuid, player.getId());
        }
        return player;
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
