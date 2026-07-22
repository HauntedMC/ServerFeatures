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
 *
 * <p>Cache-only methods are explicitly named. All methods that may consult persistence are
 * asynchronous and use the active cache as a fast path.</p>
 */
public final class PlayerIdentityResolver {

    private final PlayerDirectory playerDirectory;

    public PlayerIdentityResolver(DataRegistryApi dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").players().identities());
    }

    public PlayerIdentityResolver(PlayerDirectory playerDirectory) {
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
    }

    public CompletionStage<Optional<PlayerIdentity>> findByUuid(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByUuid(uuid);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUuid(uuid);
    }

    public CompletionStage<Optional<PlayerIdentity>> findByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByUuid(uuid);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUuid(uuid);
    }

    public CompletionStage<Optional<PlayerIdentity>> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = findActiveByUsername(username);
        return cached.isPresent()
                ? CompletableFuture.completedFuture(cached)
                : playerDirectory.findByUsernameIgnoreCase(username.trim());
    }

    public CompletionStage<Optional<PlayerIdentity>> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String normalized = identifier.trim();
        try {
            return findByUuid(UUID.fromString(normalized));
        } catch (IllegalArgumentException ignored) {
            Optional<PlayerIdentity> cached = findActiveByUsername(normalized);
            return cached.isPresent()
                    ? CompletableFuture.completedFuture(cached)
                    : playerDirectory.findByIdentifier(normalized);
        }
    }

    /**
     * Performs a persistence lookup even when no active identity is cached.
     */
    public CompletionStage<Optional<PlayerIdentity>> findPersistedByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return playerDirectory.findByUuid(uuid);
    }

    public Optional<PlayerIdentity> findActiveByUuid(UUID uuid) {
        return uuid == null ? Optional.empty() : playerDirectory.findActiveIdentityCached(uuid);
    }

    public Optional<PlayerIdentity> findActiveByUuid(String uuid) {
        return uuid == null || uuid.isBlank()
                ? Optional.empty()
                : playerDirectory.findActiveIdentityCached(uuid);
    }

    /**
     * Looks up an active identity by its current username without performing I/O.
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
        if (uuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return playerDirectory.whenReady(uuid);
    }
}
