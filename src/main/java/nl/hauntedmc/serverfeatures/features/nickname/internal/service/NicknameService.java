package nl.hauntedmc.serverfeatures.features.nickname.internal.service;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NicknameService {
    private final PlayerData players;

    public NicknameService(Nickname feature) {
        DataRegistryApi dataRegistry = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Nickname."));
        this.players = dataRegistry.players();
    }

    /**
     * Resolves an existing DataRegistry identity without creating or updating a player row.
     */
    public Optional<PlayerIdentity> getCachedPlayerIdentity(OfflinePlayer player) {
        return players.findActiveIdentityCached(player.getUniqueId());
    }

    public CompletionStage<Optional<PlayerIdentity>> findPlayerIdentity(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<PlayerIdentity> cached = players.findActiveIdentityCached(playerUuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return players.findIdentity(playerUuid);
    }

    public CompletionStage<Optional<PlayerIdentity>> findPlayerIdentity(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return players.findIdentityByIdentifier(identifier.trim());
    }

    public CompletionStage<Optional<String>> findNickname(PlayerIdentity playerIdentity) {
        if (playerIdentity == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return players.findNickname(playerIdentity.playerId());
    }

    public CompletionStage<Void> setNickname(PlayerIdentity playerIdentity, String nickname) {
        if (playerIdentity == null || playerIdentity.playerId() <= 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A persisted player identity is required."));
        }
        return players.saveNickname(playerIdentity.playerId(), nickname);
    }

    public CompletionStage<Void> removeNickname(PlayerIdentity playerIdentity) {
        if (playerIdentity == null || playerIdentity.playerId() <= 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A persisted player identity is required."));
        }
        return players.clearNickname(playerIdentity.playerId());
    }
}
