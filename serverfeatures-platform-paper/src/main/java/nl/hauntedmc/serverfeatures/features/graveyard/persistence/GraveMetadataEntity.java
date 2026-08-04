package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "player_graveyard_graves",
        indexes = {
                @Index(name = "idx_player_grave_owner_state", columnList = "owner_uuid, state"),
                @Index(name = "idx_player_grave_server_state", columnList = "server_id, state"),
                @Index(name = "idx_player_grave_world_state", columnList = "server_id, world_uuid, state"),
                @Index(name = "idx_player_grave_expiry", columnList = "server_id, expires_active_ms, state"),
                @Index(
                        name = "uq_player_grave_scope_identifier",
                        columnList = "server_id, inventory_scope, short_id",
                        unique = true
                )
        }
)
public class GraveMetadataEntity {
    @Id
    @Column(name = "grave_id", length = 36, nullable = false)
    private String graveId;

    @Column(name = "short_id", length = 160, nullable = false)
    private String shortId;

    @Column(name = "owner_uuid", length = 36, nullable = false)
    private String ownerUuid;

    @Column(name = "owner_player_id")
    private Long ownerPlayerId;

    @Column(name = "owner_name", length = 64, nullable = false)
    private String ownerName;

    @Column(name = "server_id", length = 100, nullable = false)
    private String serverId;

    @Column(name = "inventory_scope", length = 100, nullable = false)
    private String inventoryScope;

    @Column(name = "world_uuid", length = 36, nullable = false)
    private String worldUuid;

    @Column(name = "world_key", length = 160, nullable = false)
    private String worldKey;

    @Column(name = "death_x", nullable = false)
    private double deathX;

    @Column(name = "death_y", nullable = false)
    private double deathY;

    @Column(name = "death_z", nullable = false)
    private double deathZ;

    @Column(name = "grave_x", nullable = false)
    private double graveX;

    @Column(name = "grave_y", nullable = false)
    private double graveY;

    @Column(name = "grave_z", nullable = false)
    private double graveZ;

    @Column(name = "grave_yaw", nullable = false)
    private float graveYaw;

    @Column(name = "placement_type", length = 32, nullable = false)
    private String placementType;

    @Column(name = "state", length = 32, nullable = false)
    private String state;

    @Column(name = "created_wall_ms", nullable = false)
    private long createdWallMillis;

    @Column(name = "created_active_ms", nullable = false)
    private long createdActiveMillis;

    @Column(name = "expires_active_ms", nullable = false)
    private long expiresActiveMillis;

    @Column(name = "paused_remaining_ms")
    private Long pausedRemainingMillis;

    @Column(name = "item_entry_count", nullable = false)
    private int itemEntryCount;

    @Column(name = "remaining_xp", nullable = false)
    private int remainingExperience;

    @Column(name = "payload_revision", nullable = false)
    private long payloadRevision;

    @Column(name = "payload_checksum", length = 64, nullable = false)
    private String payloadChecksum;

    @Column(name = "operation_token", length = 36)
    private String operationToken;

    @Column(name = "operation_started_ms")
    private Long operationStartedMillis;

    @Column(name = "owner_was_vanished", nullable = false)
    private boolean ownerWasVanished;

    @Column(name = "death_cause", length = 160)
    private String deathCause;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "completed_at")
    private Long completedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public String getGraveId() { return graveId; }
    public void setGraveId(String graveId) { this.graveId = graveId; }
    public String getShortId() { return shortId; }
    public void setShortId(String shortId) { this.shortId = shortId; }
    public String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; }
    public Long getOwnerPlayerId() { return ownerPlayerId; }
    public void setOwnerPlayerId(Long ownerPlayerId) { this.ownerPlayerId = ownerPlayerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getInventoryScope() { return inventoryScope; }
    public void setInventoryScope(String inventoryScope) { this.inventoryScope = inventoryScope; }
    public String getWorldUuid() { return worldUuid; }
    public void setWorldUuid(String worldUuid) { this.worldUuid = worldUuid; }
    public String getWorldKey() { return worldKey; }
    public void setWorldKey(String worldKey) { this.worldKey = worldKey; }
    public double getDeathX() { return deathX; }
    public void setDeathX(double deathX) { this.deathX = deathX; }
    public double getDeathY() { return deathY; }
    public void setDeathY(double deathY) { this.deathY = deathY; }
    public double getDeathZ() { return deathZ; }
    public void setDeathZ(double deathZ) { this.deathZ = deathZ; }
    public double getGraveX() { return graveX; }
    public void setGraveX(double graveX) { this.graveX = graveX; }
    public double getGraveY() { return graveY; }
    public void setGraveY(double graveY) { this.graveY = graveY; }
    public double getGraveZ() { return graveZ; }
    public void setGraveZ(double graveZ) { this.graveZ = graveZ; }
    public float getGraveYaw() { return graveYaw; }
    public void setGraveYaw(float graveYaw) { this.graveYaw = graveYaw; }
    public String getPlacementType() { return placementType; }
    public void setPlacementType(String placementType) { this.placementType = placementType; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public long getCreatedWallMillis() { return createdWallMillis; }
    public void setCreatedWallMillis(long createdWallMillis) { this.createdWallMillis = createdWallMillis; }
    public long getCreatedActiveMillis() { return createdActiveMillis; }
    public void setCreatedActiveMillis(long createdActiveMillis) { this.createdActiveMillis = createdActiveMillis; }
    public long getExpiresActiveMillis() { return expiresActiveMillis; }
    public void setExpiresActiveMillis(long expiresActiveMillis) { this.expiresActiveMillis = expiresActiveMillis; }
    public Long getPausedRemainingMillis() { return pausedRemainingMillis; }
    public void setPausedRemainingMillis(Long pausedRemainingMillis) { this.pausedRemainingMillis = pausedRemainingMillis; }
    public int getItemEntryCount() { return itemEntryCount; }
    public void setItemEntryCount(int itemEntryCount) { this.itemEntryCount = itemEntryCount; }
    public int getRemainingExperience() { return remainingExperience; }
    public void setRemainingExperience(int remainingExperience) { this.remainingExperience = remainingExperience; }
    public long getPayloadRevision() { return payloadRevision; }
    public void setPayloadRevision(long payloadRevision) { this.payloadRevision = payloadRevision; }
    public String getPayloadChecksum() { return payloadChecksum; }
    public void setPayloadChecksum(String payloadChecksum) { this.payloadChecksum = payloadChecksum; }
    public String getOperationToken() { return operationToken; }
    public void setOperationToken(String operationToken) { this.operationToken = operationToken; }
    public Long getOperationStartedMillis() { return operationStartedMillis; }
    public void setOperationStartedMillis(Long operationStartedMillis) { this.operationStartedMillis = operationStartedMillis; }
    public boolean isOwnerWasVanished() { return ownerWasVanished; }
    public void setOwnerWasVanished(boolean ownerWasVanished) { this.ownerWasVanished = ownerWasVanished; }
    public String getDeathCause() { return deathCause; }
    public void setDeathCause(String deathCause) { this.deathCause = deathCause; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
    public long getRowVersion() { return rowVersion; }
}
