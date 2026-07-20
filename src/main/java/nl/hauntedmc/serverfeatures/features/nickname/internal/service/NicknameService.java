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
        Optional<PlayerIdentity> cached = players.findActiveIdentityCached(playerUuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return players.findIdentity(playerUuid);
    }

    public CompletionStage<Optional<String>> findNickname(PlayerIdentity playerIdentity) {
        if (playerIdentity == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return players.findNickname(playerIdentity.playerId());
    }

    public void setNickname(PlayerIdentity playerIdentity, String nickname) {
        if (playerIdentity == null) {
            return;
        }
        players.saveNickname(playerIdentity.playerId(), nickname);
    }

    public void removeNickname(PlayerIdentity playerIdentity) {
        if (playerIdentity == null) {
            return;
        }
        players.clearNickname(playerIdentity.playerId());
    }
}
