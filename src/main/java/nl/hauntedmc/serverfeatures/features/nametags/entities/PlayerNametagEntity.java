package nl.hauntedmc.serverfeatures.features.nametags.entities;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_nametags")
public class PlayerNametagEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false, unique = true)
    private PlayerEntity player;

    @Column(name = "self_view", nullable = false)
    private boolean selfView;

    public PlayerNametagEntity() {
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public boolean isSelfView() {
        return selfView;
    }

    public void setSelfView(boolean selfView) {
        this.selfView = selfView;
    }
}
