package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerEntityResolver;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

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
    public CompletionStage<Optional<Boolean>> findSelfView(String playerUuid) {
        return playerResolver.findIdentityByUuid(playerUuid)
                .thenApply(identity -> identity.map(nl.hauntedmc.dataregistry.api.player.PlayerIdentity::playerId))
                .thenApply(playerId -> playerId.flatMap(this::findSelfViewByPlayerId))
                .exceptionally(exception -> {
                    feature.getLogger().warning("DB read error (selfview): " + exception.getMessage());
                    return Optional.empty();
                });
    }

    /**
     * Upsert the self-view preference using a native ON DUPLICATE KEY UPDATE into player_nametags.
     */
    public CompletionStage<Void> upsertSelfView(String playerUuid, String playerName, boolean selfView) {
        return playerResolver.findIdentityByUuid(playerUuid)
                .thenAccept(identity -> identity
                        .map(nl.hauntedmc.dataregistry.api.player.PlayerIdentity::playerId)
                        .filter(playerId -> playerId > 0L)
                        .ifPresent(playerId -> upsertSelfViewByPlayerId(playerId, selfView)))
                .exceptionally(exception -> {
                    feature.getLogger().warning("DB write error (selfview): " + exception.getMessage());
                    return null;
                });
    }

    private Optional<Boolean> findSelfViewByPlayerId(long playerId) {
        try {
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

    private void upsertSelfViewByPlayerId(long playerId, boolean selfView) {
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
    }
}
