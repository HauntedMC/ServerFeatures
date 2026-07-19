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

/**
 * Resolves DataRegistry identity snapshots to managed ORM references for feature-owned transactions.
 */
public final class PlayerEntityResolver {

    private final PlayerDirectory playerDirectory;

    public PlayerEntityResolver(DataRegistry dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").getPlayerDirectory());
    }

    public PlayerEntityResolver(PlayerDirectory playerDirectory) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
    }

    /**
     * Resolves a persisted player identity by UUID without creating or updating a DataRegistry row.
     */
    public Optional<PlayerEntity> findByUuid(String uuid) {
        return playerDirectory.findByUuid(toUuid(uuid))
                .map(identity -> {
                    PlayerEntity player = new PlayerEntity();
                    player.setId(identity.playerId());
                    player.setUuid(identity.uuid().toString());
                    player.setUsername(identity.username());
                    return player;
                });
    }

    /**
     * Resolves a persisted player identity snapshot by UUID without exposing DataRegistry ORM state.
     */
    public Optional<PlayerIdentity> identityForUuid(String uuid) {
        return playerDirectory.findByUuid(toUuid(uuid));
    }

    /**
     * Returns the lifecycle readiness future for the player's canonical DataRegistry identity.
     */
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return playerDirectory.whenReady(uuid);
    }

    /**
     * Resolves an existing player as a managed Hibernate reference in the supplied feature transaction.
     */
    public PlayerEntity resolveManaged(Session session, UUID uuid, String usernameHint) {
        if (session == null || uuid == null) {
            return null;
        }
        return playerDirectory.getActiveIdentity(uuid)
                .or(() -> playerDirectory.findByUuid(uuid))
                .map(PlayerIdentity::playerId)
                .filter(playerId -> playerId != null && playerId > 0)
                .map(playerId -> session.getReference(PlayerEntity.class, playerId))
                .orElse(null);
    }

    /**
     * Returns a managed Hibernate reference by scalar player id for feature-owned entities.
     */
    public PlayerEntity resolveManagedById(Session session, Long playerId) {
        if (session == null || playerId == null || playerId <= 0) {
            return null;
        }
        return session.getReference(PlayerEntity.class, playerId);
    }

    private static UUID toUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuid.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
