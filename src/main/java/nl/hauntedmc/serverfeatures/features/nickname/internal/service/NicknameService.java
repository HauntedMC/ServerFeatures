package nl.hauntedmc.serverfeatures.features.nickname.internal.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.OfflinePlayer;

import java.util.Optional;

public class NicknameService {
    private final Nickname feature;

    public NicknameService(Nickname feature) {
        this.feature = feature;
    }

    public Optional<PlayerEntity> getPlayerEntity(OfflinePlayer player) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery("FROM PlayerEntity WHERE uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", player.getUniqueId().toString())
                        .uniqueResultOptional());
    }

    public Optional<String> getNickname(PlayerEntity playerEntity) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery(
                                "SELECT n.nickname FROM NicknameEntity n WHERE n.playerId = :playerId", String.class)
                        .setParameter("playerId", playerEntity.getId())
                        .uniqueResultOptional());
    }

    public void setNickname(PlayerEntity playerEntity, String nickname) {
        feature.getOrmContext().runInTransaction(session ->
                session.createNativeQuery(
                                "INSERT INTO player_nicknames (player_id, nickname) VALUES (:playerId, :nickname) " +
                                        "ON DUPLICATE KEY UPDATE nickname = :nickname")
                        .setParameter("playerId", playerEntity.getId())
                        .setParameter("nickname", nickname)
                        .executeUpdate()
        );
    }

    public void removeNickname(PlayerEntity playerEntity) {
        feature.getOrmContext().runInTransaction(session ->
                session.createQuery("DELETE FROM NicknameEntity WHERE playerId = :playerId")
                        .setParameter("playerId", playerEntity.getId())
                        .executeUpdate()
        );
    }
}
