package nl.hauntedmc.serverfeatures.features.nametags.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "player_nametags")
public class PlayerNametagEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;


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

    public boolean isSelfView() {
        return selfView;
    }

    public void setSelfView(boolean selfView) {
        this.selfView = selfView;
    }
}
