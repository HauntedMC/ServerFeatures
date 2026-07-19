package nl.hauntedmc.serverfeatures.features.nickname.internal.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.OfflinePlayer;

import java.util.Optional;

public class NicknameService {
    private final PlayerData players;

    public NicknameService(Nickname feature) {
        DataRegistry dataRegistry = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Nickname."));
        this.players = dataRegistry.players();
    }

    /**
     * Resolves an existing DataRegistry identity without creating or updating a player row.
     */
    public Optional<PlayerIdentity> getPlayerIdentity(OfflinePlayer player) {
        return players.activeIdentity(player.getUniqueId())
                .or(() -> players.findIdentity(player.getUniqueId()));
    }

    public Optional<String> getNickname(PlayerIdentity playerIdentity) {
        if (playerIdentity == null) {
            return Optional.empty();
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
