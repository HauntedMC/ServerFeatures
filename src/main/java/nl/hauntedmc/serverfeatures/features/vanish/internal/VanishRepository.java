package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.features.vanish.entities.PlayerVanishEntity;
import org.hibernate.Session;

/**
 * Encapsulates ORM access for PlayerEntity & PlayerVanishEntity.
 * Keeps the service clean and testable.
 */
public class VanishRepository {

    private final Vanish feature;

    public VanishRepository(Vanish feature) {
        this.feature = feature;
    }

    /**
     * Ensures a PlayerEntity exists for the UUID. Creates one if missing.
     */
    public PlayerEntity ensurePlayerEntity(Session session, String uuid, String username) {
        PlayerEntity playerEntity = session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                .setParameter("uuid", uuid)
                .uniqueResult();

        if (playerEntity == null) {
            playerEntity = new PlayerEntity();
            playerEntity.setUuid(uuid);
            playerEntity.setUsername(username);
            session.persist(playerEntity);
        } else {
            // Optional: update latest username if changed
            if (username != null && !username.equals(playerEntity.getUsername())) {
                playerEntity.setUsername(username);
            }
        }
        return playerEntity;
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
    public void upsertVanish(String uuid, String username, boolean vanished) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerEntity player = ensurePlayerEntity(session, uuid, username);

            PlayerVanishEntity row = session.createQuery(
                            "SELECT v FROM PlayerVanishEntity v WHERE v.player.id = :pid", PlayerVanishEntity.class)
                    .setParameter("pid", player.getId())
                    .uniqueResult();

            if (row == null) {
                row = new PlayerVanishEntity();
                row.setPlayer(player);
                row.setVanished(vanished);
                session.persist(row);
            } else {
                row.setVanished(vanished);
                session.merge(row);
            }
            return null;
        });
    }

    /* --------------------- Helpers --------------------- */

    private PlayerVanishEntity findVanishByUuid(Session session, String uuid) {
        return session.createQuery(
                        "SELECT v FROM PlayerVanishEntity v WHERE v.player.uuid = :uuid", PlayerVanishEntity.class)
                .setParameter("uuid", uuid)
                .uniqueResult();
    }
}
