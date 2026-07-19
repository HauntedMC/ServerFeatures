package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerEntityResolver;

import java.util.Optional;

public class NametagDBService {

    private final Nametags feature;
    private final ORMContext orm;
    private final PlayerEntityResolver playerResolver;

    public NametagDBService(Nametags feature) {
        this.feature = feature;
        this.orm = feature.getOrmContext();
        this.playerResolver = new PlayerEntityResolver(
                feature.getPlugin().getDataRegistry()
                        .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Nametags."))
        );
    }

    /**
     * Read the persisted self-view preference for a player by UUID.
     * Returns Optional.empty() when not set or player not found.
     */
    public Optional<Boolean> getSelfView(String playerUuid) {
        try {
            Optional<Long> playerIdOpt = playerResolver.findIdentityByUuid(playerUuid)
                    .map(nl.hauntedmc.dataregistry.api.player.PlayerIdentity::playerId);

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
            Long playerId = playerResolver.findIdentityByUuid(playerUuid)
                    .map(nl.hauntedmc.dataregistry.api.player.PlayerIdentity::playerId)
                    .orElse(null);
            if (playerId == null || playerId <= 0) {
                return;
            }
            orm.runInTransaction(session -> {
                session.createNativeMutationQuery(
                                "INSERT INTO player_nametags (player_id, self_view) " +
                                        "VALUES (:playerId, :selfView) " +
                                        "ON DUPLICATE KEY UPDATE self_view = :selfView")
                        .setParameter("playerId", playerId)
                        .setParameter("selfView", selfView)
                        .executeUpdate();

                return null;
            });
        } catch (Exception ex) {
            feature.getLogger().warning("DB write error (selfview): " + ex.getMessage());
        }
    }
}
