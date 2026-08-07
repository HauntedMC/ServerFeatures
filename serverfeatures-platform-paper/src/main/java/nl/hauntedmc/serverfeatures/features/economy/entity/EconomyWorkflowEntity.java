package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Durable post-commit fulfilment event for a charged Economy workflow. */
@Entity
@Table(
        name = "economy_workflow",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_economy_workflow_source_key", columnNames = {"source", "workflow_key"}),
                @UniqueConstraint(name = "uq_economy_workflow_operation", columnNames = "operation_id")
        },
        indexes = {
                @Index(name = "idx_economy_workflow_ready", columnList = "state,available_at,created_at"),
                @Index(name = "idx_economy_workflow_event_type", columnList = "event_type,state,available_at")
        }
)
public class EconomyWorkflowEntity {
    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;
    @Column(name = "source", length = 64, nullable = false)
    private String source;
    @Column(name = "workflow_key", length = 160, nullable = false)
    private String workflowKey;
    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;
    @Column(name = "operation_id", length = 36, nullable = false)
    private String operationId;
    @Column(name = "player_id", nullable = false)
    private long playerId;
    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;
    @Column(name = "player_name", length = 32, nullable = false)
    private String playerName;
    @Column(name = "currency_id", length = 64, nullable = false)
    private String currencyId;
    @Column(name = "scope_key", length = 128, nullable = false)
    private String scopeKey;
    @Column(name = "amount", precision = 38, scale = 8, nullable = false)
    private BigDecimal amount;
    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;
    @Column(name = "metadata_json", length = 4096, nullable = false)
    private String metadataJson;
    @Column(name = "state", length = 24, nullable = false)
    private String state;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "available_at", nullable = false)
    private long availableAt;
    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;
    @Column(name = "lease_expires_at")
    private Long leaseExpiresAt;
    @Column(name = "last_error", length = 512, nullable = false)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
    @Column(name = "delivered_at")
    private Long deliveredAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getWorkflowKey() { return workflowKey; }
    public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getCurrencyId() { return currencyId; }
    public void setCurrencyId(String currencyId) { this.currencyId = currencyId; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public long getAvailableAt() { return availableAt; }
    public void setAvailableAt(long availableAt) { this.availableAt = availableAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Long getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Long leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public Long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Long deliveredAt) { this.deliveredAt = deliveredAt; }
}
