package nl.hauntedmc.serverfeatures.features.glow.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

/**
 * Persists a player's glow status and selected effect.
 * Uses player_id as the primary key (shared PK with PlayerEntity).
 */
@Entity
@Table(name = "player_glow_states")
public class PlayerGlowStateEntity {

    /** Primary key equals the player's id in PlayerEntity */
    @Id
    @Column(name = "player_id")
    private Long playerId;

    /** One-to-one link to PlayerEntity sharing the same PK */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    /** Whether glow is enabled for this player */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Identifier of the current glow effect (e.g. "red", "rainbow"). Nullable when disabled. */
    @Column(name = "effect_id", length = 100)
    private String effectId;

    public PlayerGlowStateEntity() {}

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEffectId() {
        return effectId;
    }

    public void setEffectId(String effectId) {
        this.effectId = effectId;
    }
}
