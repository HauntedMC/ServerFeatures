package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.features.vanish.entities.PlayerVanishEntity;
import org.hibernate.Session;

/**
 * Encapsulates ORM access for scalar player ids and PlayerVanishEntity.
 * Keeps the service clean and testable.
 */
public class VanishRepository {

    private final Vanish feature;
    private final PlayerIdentityResolver playerResolver;

    public VanishRepository(Vanish feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Vanish."))
        );
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

    /**
     * Returns whether the player (by UUID) is persisted as vanished.
     */
    public boolean isPersistedVanished(String uuid) {
        return feature.getOrmContext().runInTransaction(session -> {
            PlayerVanishEntity vanish = findVanishByUuid(session, uuid);
            return vanish != null && vanish.isVanished();
        });
    }

    /**
     * Sets/updates persisted vanish state (UPSERT).
     */
    public void upsertVanish(String uuid, boolean vanished) {
        feature.getOrmContext().runInTransaction(session -> {
            upsertVanish(session, uuid, vanished);
            return null;
        });
    }

    void upsertVanish(Session session, String uuid, boolean vanished) {
        Long playerId = findExistingPlayerId(uuid);
        if (playerId == null) {
            return;
        }

        PlayerVanishEntity row = session.createQuery(
                        "SELECT v FROM PlayerVanishEntity v WHERE v.playerId = :pid", PlayerVanishEntity.class)
                .setParameter("pid", playerId)
                .uniqueResult();

        if (row == null) {
            row = new PlayerVanishEntity();
            row.setPlayerId(playerId);
            row.setVanished(vanished);
            session.persist(row);
        } else {
            row.setVanished(vanished);
        }
    }

    /* --------------------- Helpers --------------------- */

    private PlayerVanishEntity findVanishByUuid(Session session, String uuid) {
        Long playerId = findExistingPlayerId(uuid);
        if (playerId == null) {
            return null;
        }
        return session.createQuery(
                        "SELECT v FROM PlayerVanishEntity v WHERE v.playerId = :playerId", PlayerVanishEntity.class)
                .setParameter("playerId", playerId)
                .uniqueResult();
    }
}
