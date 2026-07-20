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
 * <p>
 * The active identity cache is the fast path. A feature can, however, run after DataRegistry has committed a
 * player row but before its cache entry is visible (or after cache eviction on disconnect). In that case this
 * resolver reads the canonical row in the caller-owned transaction instead of treating a persisted player as
 * unknown.
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
     * Resolves an active identity cache entry, falling back to the canonical persisted player row when needed.
     */
    public PlayerEntity resolveManaged(Session session, UUID uuid) {
        if (session == null || uuid == null) {
            return null;
        }
        return playerDirectory.findActiveIdentityCached(uuid)
                .map(PlayerIdentity::playerId)
                .filter(playerId -> playerId != null && playerId > 0)
                .map(playerId -> session.getReference(PlayerEntity.class, playerId))
                .orElseGet(() -> findPersistedManaged(session, uuid.toString()));
    }

    public PlayerEntity resolveManaged(Session session, String uuid) {
        if (session == null || uuid == null || uuid.isBlank()) {
            return null;
        }
        return playerDirectory.findActiveIdentityCached(uuid)
                .map(PlayerIdentity::playerId)
                .filter(playerId -> playerId != null && playerId > 0)
                .map(playerId -> session.getReference(PlayerEntity.class, playerId))
                .orElseGet(() -> findPersistedManaged(session, uuid));
    }

    public PlayerEntity resolveManagedById(Session session, Long playerId) {
        if (session == null || playerId == null || playerId <= 0) {
            return null;
        }
        return session.getReference(PlayerEntity.class, playerId);
    }

    private static PlayerEntity findPersistedManaged(Session session, String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return null;
        }
        return session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                        PlayerEntity.class
                )
                .setParameter("uuid", normalizedUuid)
                .setMaxResults(1)
                .uniqueResultOptional()
                .orElse(null);
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuid.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PlayerEntity toEntity(PlayerIdentity identity) {
        PlayerEntity player = new PlayerEntity();
        player.setId(identity.playerId());
        player.setUuid(identity.uuid().toString());
        player.setUsername(identity.username());
        return player;
    }
}
