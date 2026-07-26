package nl.hauntedmc.serverfeatures.features.vanish.entities;

import jakarta.persistence.*;

/**
 * player_vanish table with the player's DB id as the PRIMARY KEY.
 * Scalar primary key mapped to the canonical DataRegistry player id.
 */
@Entity
@Table(name = "player_vanish")
public class PlayerVanishEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;


    @Column(name = "vanished", nullable = false)
    private boolean vanished;

    public PlayerVanishEntity() {
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public boolean isVanished() {
        return vanished;
    }

    public void setVanished(boolean vanished) {
        this.vanished = vanished;
    }
}
