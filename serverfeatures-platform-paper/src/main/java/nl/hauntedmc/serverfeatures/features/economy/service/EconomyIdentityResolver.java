package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.features.economy.Economy;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Resolves canonical DataRegistry identities for Economy operations.
 *
 * <p>Economy never treats a denormalized account name as an authority: a UUID or name lookup
 * must first be confirmed by DataRegistry. This is especially important because player names
 * may change or be reassigned.</p>
 */
final class EconomyIdentityResolver {
    private static final long SYNCHRONOUS_TIMEOUT_MILLIS = 1_000L;

    private final PlayerIdentityResolver resolver;
    private final BooleanSupplier closed;

    EconomyIdentityResolver(Economy feature, BooleanSupplier closed) {
        Objects.requireNonNull(feature, "feature");
        this.closed = Objects.requireNonNull(closed, "closed");
        this.resolver = new PlayerIdentityResolver(
                feature.getPlugin().getDataRegistry().orElseThrow(() -> new IllegalStateException(
                        "Economy requires DataRegistry"
                ))
        );
    }

    /** Resolves and validates the UUID/player-id pair supplied by an API account reference. */
    CompletionStage<Identity> resolve(EconomyAccountRef account) {
        Objects.requireNonNull(account, "account");
        if (account.playerUuid() == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("Account player UUID must not be null")
            );
        }
        return resolver.findByUuid(account.playerUuid()).thenApply(optional -> optional
                .map(EconomyIdentityResolver::identity)
                .map(resolved -> validatePlayerId(account, resolved))
                .orElseThrow(() -> new UnknownPlayerException("Unknown player: " + account.playerUuid())));
    }

    /** Resolves a UUID/name/player-id string using DataRegistry's supported identifier lookup. */
    CompletionStage<Identity> resolveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("Player identifier must not be blank")
            );
        }
        return resolver.findByIdentifier(identifier.trim()).thenApply(optional -> optional
                .map(EconomyIdentityResolver::identity)
                .orElseThrow(() -> new UnknownPlayerException("Unknown player: " + identifier)));
    }

    /** Returns the already-resolved identity for an online player, without blocking. */
    Optional<Identity> active(UUID playerUuid) {
        return playerUuid == null ? Optional.empty() : resolver.findActiveByUuid(playerUuid).map(EconomyIdentityResolver::identity);
    }

    /** Starts an asynchronous lookup used by the preload cache. */
    CompletionStage<Optional<Identity>> whenReady(UUID playerUuid) {
        return resolver.whenReady(playerUuid).thenApply(optional -> optional.map(EconomyIdentityResolver::identity));
    }

    /** Resolves a canonical identity for a message that also carries a committed player id. */
    CompletionStage<Identity> resolveCanonical(UUID playerUuid, long expectedPlayerId) {
        return resolver.findByUuid(playerUuid).thenCompose(resolved -> {
            if (resolved.isEmpty() || closed.getAsBoolean()) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Unknown player identity: " + playerUuid
                ));
            }
            Identity identity = identity(resolved.get());
            if (identity.playerId() != expectedPlayerId) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Player identity mismatch for " + playerUuid
                ));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(identity);
        });
    }

    /** Blocking adapter required by Vault's synchronous SPI. */
    Optional<Identity> resolveSync(OfflinePlayer player) {
        if (player == null || closed.getAsBoolean()) {
            return Optional.empty();
        }
        Optional<Identity> active = active(player.getUniqueId());
        return active.isPresent() ? active : await(resolver.findByUuid(player.getUniqueId()), player.getUniqueId().toString());
    }

    /** Blocking name lookup required by Vault's legacy synchronous SPI. */
    Optional<Identity> resolveSync(String playerName) {
        if (playerName == null || playerName.isBlank() || closed.getAsBoolean()) {
            return Optional.empty();
        }
        String normalized = playerName.trim();
        Optional<PlayerIdentity> active = resolver.findActiveByUsername(normalized);
        return active.map(EconomyIdentityResolver::identity)
                .or(() -> await(resolver.findByUsername(normalized), normalized));
    }

    private Optional<Identity> await(CompletionStage<Optional<PlayerIdentity>> lookup, String identifier) {
        try {
            return lookup.toCompletableFuture()
                    .get(SYNCHRONOUS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .map(EconomyIdentityResolver::identity);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving player identity " + identifier, exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Could not resolve player identity " + identifier, EconomyFailure.unwrap(exception));
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out resolving player identity " + identifier, exception);
        }
    }

    private static Identity validatePlayerId(EconomyAccountRef account, Identity identity) {
        if (account.playerId() != null && account.playerId() > 0L && account.playerId() != identity.playerId()) {
            throw new IllegalArgumentException("Player ID does not match UUID: " + account.playerUuid());
        }
        return identity;
    }

    static Identity identity(PlayerIdentity identity) {
        return new Identity(identity.playerId(), identity.uuid(), identity.username());
    }
}
