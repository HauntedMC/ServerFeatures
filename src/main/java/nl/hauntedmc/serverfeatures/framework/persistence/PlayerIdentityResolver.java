package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Resolves immutable DataRegistry identities for scalar player-id feature mappings.
 */
public final class PlayerIdentityResolver {

    private final PlayerDirectory playerDirectory;

    public PlayerIdentityResolver(DataRegistryApi dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").players().identities());
    }

    public PlayerIdentityResolver(PlayerDirectory playerDirectory) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
    }

    public CompletionStage<Optional<PlayerIdentity>> findPersistedByUuid(String uuid) {
        return playerDirectory.findByUuid(uuid);
    }

    public Optional<PlayerIdentity> findActiveByUuid(UUID uuid) {
        return playerDirectory.findActiveIdentityCached(uuid);
    }

    public Optional<PlayerIdentity> findActiveByUuid(String uuid) {
        return playerDirectory.findActiveIdentityCached(uuid);
    }

    /**
     * Looks up an active identity by its current username without performing I/O.
     *
     * <p>This is appropriate for operations initiated for an online player. Callers
     * that need to address offline players must use the asynchronous directory API
     * instead.</p>
     */
    public Optional<PlayerIdentity> findActiveByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return playerDirectory.snapshotActiveIdentities().values().stream()
                .filter(identity -> username.equalsIgnoreCase(identity.username()))
                .findFirst();
    }

    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return playerDirectory.whenReady(uuid);
    }
}
