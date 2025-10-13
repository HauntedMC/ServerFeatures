package nl.hauntedmc.serverfeatures.features.vanish.entities;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

/**
 * player_vanish table with the player's DB id as the PRIMARY KEY.
 * Shared primary key mapping to PlayerEntity.id via @MapsId.
 */
@Entity
@Table(name = "player_vanish")
public class PlayerVanishEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false, unique = true)
    private PlayerEntity player;

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

    public PlayerEntity getPlayer() {
        return player;
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public boolean isVanished() {
        return vanished;
    }

    public void setVanished(boolean vanished) {
        this.vanished = vanished;
    }
}
