package nl.hauntedmc.serverfeatures.features.autopickup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_auto_pickup_settings")
public class PlayerAutoPickupSettingEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "write_revision", nullable = false)
    private long writeRevision;

    public PlayerAutoPickupSettingEntity() {
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

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getWriteRevision() {
        return writeRevision;
    }

    public void setWriteRevision(long writeRevision) {
        this.writeRevision = writeRevision;
    }
}
