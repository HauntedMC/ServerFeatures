package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.hibernate.Session;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Resolves DataRegistry identity snapshots to managed ORM references for feature-owned transactions.
 */
public final class PlayerEntityResolver {

    private final PlayerDirectory playerDirectory;

    public PlayerEntityResolver(DataRegistry dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").players().identities());
    }

    public PlayerEntityResolver(PlayerDirectory playerDirectory) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
    }

    public CompletionStage<Optional<PlayerEntity>> findByUuid(String uuid) {
        return playerDirectory.findByUuid(uuid)
                .thenApply(identity -> identity.map(PlayerEntityResolver::toEntity));
    }

    public CompletionStage<Optional<PlayerIdentity>> findIdentityByUuid(String uuid) {
        return playerDirectory.findByUuid(uuid);
    }

    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return playerDirectory.whenReady(uuid);
    }

    /**
     * Resolves only the active DataRegistry cache into a managed Hibernate reference.
     * Persistent lookup must happen before entering the feature transaction.
     */
    public PlayerEntity resolveManaged(Session session, UUID uuid) {
        if (session == null || uuid == null) {
            return null;
        }
        return playerDirectory.findActiveIdentityCached(uuid)
                .map(PlayerIdentity::playerId)
                .filter(playerId -> playerId != null && playerId > 0)
                .map(playerId -> session.getReference(PlayerEntity.class, playerId))
                .orElse(null);
    }

    public PlayerEntity resolveManaged(Session session, String uuid) {
        if (session == null || uuid == null || uuid.isBlank()) {
            return null;
        }
        return playerDirectory.findActiveIdentityCached(uuid)
                .map(PlayerIdentity::playerId)
                .filter(playerId -> playerId != null && playerId > 0)
                .map(playerId -> session.getReference(PlayerEntity.class, playerId))
                .orElse(null);
    }

    public PlayerEntity resolveManagedById(Session session, Long playerId) {
        if (session == null || playerId == null || playerId <= 0) {
            return null;
        }
        return session.getReference(PlayerEntity.class, playerId);
    }

    private static PlayerEntity toEntity(PlayerIdentity identity) {
        PlayerEntity player = new PlayerEntity();
        player.setId(identity.playerId());
        player.setUuid(identity.uuid().toString());
        player.setUsername(identity.username());
        return player;
    }
}
