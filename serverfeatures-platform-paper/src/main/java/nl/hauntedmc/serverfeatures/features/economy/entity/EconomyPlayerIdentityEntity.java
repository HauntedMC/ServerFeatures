package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/** Canonical immutable player ID to UUID ownership used by all economy scopes and currencies. */
@Entity
@Table(
        name = "player_economy_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_economy_identity_uuid",
                columnNames = "player_uuid"
        )
)
public class EconomyPlayerIdentityEntity {
    @Id
    @Column(name = "player_id", nullable = false)
    private long playerId;
    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;
    @Column(name = "player_name", length = 32, nullable = false)
    private String playerName;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public long getVersion() { return version; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
