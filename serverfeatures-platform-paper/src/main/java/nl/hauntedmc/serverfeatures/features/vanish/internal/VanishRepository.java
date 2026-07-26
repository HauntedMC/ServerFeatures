package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.features.vanish.entities.PlayerVanishEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.hibernate.Session;

/**
 * Encapsulates ORM access for scalar player ids and PlayerVanishEntity.
 */
public class VanishRepository {

    private final Vanish feature;
    private final PlayerIdentityResolver playerResolver;

    public VanishRepository(Vanish feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Vanish.")));
    }

    VanishRepository(Vanish feature, DataRegistryApi dataRegistry) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(dataRegistry);
    }

    VanishRepository(Vanish feature, PlayerDirectory playerDirectory) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(playerDirectory);
    }

    public Long findExistingPlayerId(String uuid) {
        return playerResolver.findActiveByUuid(uuid).map(PlayerIdentity::playerId).orElse(null);
    }

    public boolean isPersistedVanished(String uuid) {
        Long playerId = findExistingPlayerId(uuid);
        return playerId != null && isPersistedVanished(playerId);
    }

    public boolean isPersistedVanished(long playerId) {
        if (playerId <= 0L) {
            return false;
        }
        return feature.getOrmContext().runInTransaction(session -> {
            PlayerVanishEntity vanish = findVanishByPlayerId(session, playerId);
            return vanish != null && vanish.isVanished();
        });
    }

    public void upsertVanish(String uuid, boolean vanished) {
        Long playerId = findExistingPlayerId(uuid);
        if (playerId != null) {
            upsertVanish(playerId, vanished);
        }
    }

    public void upsertVanish(long playerId, boolean vanished) {
        if (playerId <= 0L) {
            return;
        }
        feature.getOrmContext().runInTransaction(session -> {
            upsertVanish(session, playerId, vanished);
            return null;
        });
    }

    void upsertVanish(Session session, String uuid, boolean vanished) {
        Long playerId = findExistingPlayerId(uuid);
        if (playerId != null) {
            upsertVanish(session, playerId, vanished);
        }
    }

    void upsertVanish(Session session, long playerId, boolean vanished) {
        if (playerId <= 0L) {
            return;
        }

        PlayerVanishEntity row = findVanishByPlayerId(session, playerId);
        if (row == null) {
            row = new PlayerVanishEntity();
            row.setPlayerId(playerId);
            row.setVanished(vanished);
            session.persist(row);
        } else {
            row.setVanished(vanished);
        }
    }

    private PlayerVanishEntity findVanishByPlayerId(Session session, long playerId) {
        return session.createQuery(
                        "SELECT v FROM PlayerVanishEntity v WHERE v.playerId = :playerId", PlayerVanishEntity.class)
                .setParameter("playerId", playerId)
                .uniqueResult();
    }
}
