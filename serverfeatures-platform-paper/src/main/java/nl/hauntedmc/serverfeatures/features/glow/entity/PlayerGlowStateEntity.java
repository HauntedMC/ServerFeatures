package nl.hauntedmc.serverfeatures.features.glow.entity;

import jakarta.persistence.*;

/**
 * Persists a player's glow status and selected effect.
 * Uses the DataRegistry player id as a scalar primary key.
 */
@Entity
@Table(name = "player_glow_states")
public class PlayerGlowStateEntity {

    /**
     * Primary key equals the canonical DataRegistry player id.
     */
    @Id
    @Column(name = "player_id")
    private Long playerId;


    /**
     * Whether glow is enabled for this player
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * Identifier of the current glow effect (e.g. "red", "rainbow"). Nullable when disabled.
     */
    @Column(name = "effect_id", length = 100)
    private String effectId;

    public PlayerGlowStateEntity() {
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
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
