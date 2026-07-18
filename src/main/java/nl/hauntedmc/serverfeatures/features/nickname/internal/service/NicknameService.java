package nl.hauntedmc.serverfeatures.features.nickname.internal.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerNicknameRepository;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.OfflinePlayer;

import java.util.Optional;

public class NicknameService {
    private final PlayerRepository playerRepository;
    private final PlayerNicknameRepository playerNicknameRepository;

    public NicknameService(Nickname feature) {
        DataRegistry dataRegistry = feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Nickname."));
        this.playerRepository = dataRegistry.getPlayerRepository();
        this.playerNicknameRepository = dataRegistry.getPlayerNicknameRepository();
    }

    public Optional<PlayerEntity> getPlayerEntity(OfflinePlayer player) {
        String uuid = player.getUniqueId().toString();
        return playerRepository.getActivePlayer(uuid)
                .or(() -> playerRepository.findByUUID(uuid));
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
}
