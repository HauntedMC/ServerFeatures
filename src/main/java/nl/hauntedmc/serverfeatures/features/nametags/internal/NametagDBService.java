package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;

import java.util.Optional;

public class NametagDBService {

    private final Nametags feature;
    private final ORMContext orm;

    public NametagDBService(Nametags feature) {
        this.feature = feature;
        this.orm = feature.getOrmContext();
    }

    /**
     * Read the persisted self-view preference for a player by UUID.
     * Returns Optional.empty() when not set or player not found.
     */
    public Optional<Boolean> getSelfView(String playerUuid) {
        try {
            Optional<Long> playerIdOpt = orm.runInTransaction(session ->
                    session.createSelectionQuery(
                                    "SELECT p.id FROM PlayerEntity p WHERE p.uuid = :uuid",
                                    Long.class)
                            .setParameter("uuid", playerUuid)
                            .uniqueResultOptional());

            if (playerIdOpt.isEmpty()) {
                return Optional.empty();
            }

            Long playerId = playerIdOpt.get();
            return orm.runInTransaction(session ->
                    session.createSelectionQuery(
                                    "SELECT n.selfView FROM PlayerNametagEntity n WHERE n.playerId = :playerId",
                                    Boolean.class)
                            .setParameter("playerId", playerId)
                            .uniqueResultOptional());
        } catch (Exception e) {
            feature.getLogger().warning("DB read error (selfview): " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Upsert the self-view preference using a native ON DUPLICATE KEY UPDATE into player_nametags.
     */
    public void upsertSelfView(String playerUuid, String playerName, boolean selfView) {
        try {
            orm.runInTransaction(session -> {
                Optional<Long> playerIdOpt = session.createSelectionQuery(
                                "SELECT p.id FROM PlayerEntity p WHERE p.uuid = :uuid",
                                Long.class)
                        .setParameter("uuid", playerUuid)
                        .uniqueResultOptional();

                if (playerIdOpt.isEmpty()) {
                    throw new IllegalStateException("PlayerEntity not found for UUID " + playerUuid + " (" + playerName + ")");
                }

                session.createNativeMutationQuery(
                                "INSERT INTO player_nametags (player_id, self_view) " +
                                        "VALUES (:playerId, :selfView) " +
                                        "ON DUPLICATE KEY UPDATE self_view = :selfView")
                        .setParameter("playerId", playerIdOpt.get())
                        .setParameter("selfView", selfView)
                        .executeUpdate();

                return null;
            });
        } catch (Exception ex) {
            feature.getLogger().warning("DB write error (selfview): " + ex.getMessage());
        }
    }
}
