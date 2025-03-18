package nl.hauntedmc.serverfeatures.features.nickname.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_nicknames")
public class NicknameEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId; // Primary Key (Also FK to PlayerEntity)

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", referencedColumnName = "id", nullable = false, unique = true)
    private PlayerEntity player;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    public NicknameEntity() {}

    public NicknameEntity(PlayerEntity player, String nickname) {
        this.playerId = player.getId();
        this.player = player;
        this.nickname = nickname;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
        this.playerId = player.getId();
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
