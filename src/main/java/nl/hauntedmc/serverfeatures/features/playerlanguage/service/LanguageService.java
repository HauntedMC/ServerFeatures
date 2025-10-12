package nl.hauntedmc.serverfeatures.features.playerlanguage.service;

import nl.hauntedmc.serverfeatures.api.io.localization.Language;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.serverfeatures.features.playerlanguage.api.LanguageAPI;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageService implements LanguageAPI {

    private static final Language FALLBACK = Language.EN; // safe default if nothing cached
    private final ORMContext orm;

    // In-memory per-session cache (populated on join)
    private final Map<UUID, Language> langCache = new ConcurrentHashMap<>();
    // Optional micro-cache to avoid re-querying PlayerEntity primary key on set()
    private final Map<UUID, Long> idCache = new ConcurrentHashMap<>();

    public LanguageService(ORMContext orm) {
        this.orm = orm;
    }

    /**
     * Load language from DB once on join. If missing, do not create;
     * simply leave cache empty (get() will use FALLBACK).
     */
    public void warm(UUID uuid) {
        orm.runInTransaction(session -> {
            PlayerEntity p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                    .setParameter("u", uuid.toString())
                    .uniqueResult();
            if (p == null) return null;

            Long pid = p.getId();
            idCache.put(uuid, pid);

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple != null && ple.getLanguage() != null) {
                langCache.put(uuid, fromCode(ple.getLanguage()));
            }
            return null;
        });
    }

    /** Clear per-session state on quit. */
    public void forget(UUID uuid) {
        langCache.remove(uuid);
        idCache.remove(uuid);
    }

    @Override
    public Language get(UUID playerUuid) {
        return langCache.getOrDefault(playerUuid, FALLBACK);
    }

    @Override
    public void set(UUID playerUuid, Language language) {
        Objects.requireNonNull(language, "language");

        orm.runInTransaction(session -> {
            Long pid = idCache.get(playerUuid);
            PlayerEntity p;

            if (pid == null) {
                p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                        .setParameter("u", playerUuid.toString())
                        .uniqueResult();
                if (p == null) return null;
                pid = p.getId();
                idCache.put(playerUuid, pid);
            } else {
                p = session.get(PlayerEntity.class, pid);
                if (p == null) {
                    idCache.remove(playerUuid);
                    return null;
                }
            }

            String code = toCode(language);

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple == null) {
                ple = new PlayerLanguageEntity();
                ple.setPlayer(p);
                ple.setLanguage(code);
                session.persist(ple);
            } else {
                ple.setLanguage(code);
                session.merge(ple);
            }
            return null;
        });

        langCache.put(playerUuid, language);
    }

    private static String toCode(Language lang) {
        return (lang == null) ? FALLBACK.name() : lang.name().toUpperCase(Locale.ROOT);
    }

    private static Language fromCode(String code) {
        if (code == null || code.isBlank()) return FALLBACK;
        try {
            return Language.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FALLBACK;
        }
    }
}
