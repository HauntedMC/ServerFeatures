package nl.hauntedmc.serverfeatures.features.nickname.internal.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.repository.PlayerNicknameRepository;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.OfflinePlayer;

import java.util.Optional;

public class NicknameService {
    private final PlayerDirectory playerDirectory;
    private final PlayerNicknameRepository playerNicknameRepository;

    public NicknameService(Nickname feature) {
        DataRegistry dataRegistry = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Nickname."));
        this.playerDirectory = dataRegistry.getPlayerDirectory();
        this.playerNicknameRepository = dataRegistry.getPlayerNicknameRepository();
    }

    /**
     * Resolves an existing player identity as a detached entity for legacy nickname call sites.
     */
    public Optional<PlayerEntity> getPlayerEntity(OfflinePlayer player) {
        return playerDirectory.getActiveIdentity(player.getUniqueId())
                .or(() -> playerDirectory.findByUuid(player.getUniqueId()))
                .map(NicknameService::toEntity);
    }

    public Optional<String> getNickname(PlayerEntity playerEntity) {
        if (playerEntity == null || playerEntity.getId() == null) {
            return Optional.empty();
        }
        return playerNicknameRepository.findNicknameByPlayerId(playerEntity.getId());
    }

    public void setNickname(PlayerEntity playerEntity, String nickname) {
        if (playerEntity == null || playerEntity.getId() == null) {
            return;
        }
        playerNicknameRepository.saveOrUpdate(playerEntity.getId(), nickname);
    }

    public void removeNickname(PlayerEntity playerEntity) {
        if (playerEntity == null || playerEntity.getId() == null) {
            return;
        }
        playerNicknameRepository.deleteByPlayerId(playerEntity.getId());
    }

    private static PlayerEntity toEntity(PlayerIdentity identity) {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(identity.playerId());
        entity.setUuid(identity.uuid().toString());
        entity.setUsername(identity.username());
        return entity;
    }
}
