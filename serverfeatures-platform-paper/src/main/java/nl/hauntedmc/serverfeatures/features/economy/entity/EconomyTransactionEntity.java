package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "system_economy_transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_economy_transaction_idempotency",
                columnNames = {"source", "idempotency_key_hash"}
        ),
        indexes = {
                @Index(name = "idx_economy_transaction_scope", columnList = "currency_id,scope_key,created_at"),
                @Index(name = "idx_economy_transaction_operation", columnList = "operation_id")
        }
)
public class EconomyTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "operation_id", length = 36, nullable = false, unique = true)
    private String operationId;
    @Column(name = "source", length = 64, nullable = false)
    private String source;
    @Column(name = "idempotency_key", length = 160, nullable = false)
    private String idempotencyKey;
    @Column(name = "idempotency_key_hash", length = 64, nullable = false)
    private String idempotencyKeyHash;
    @Column(name = "request_fingerprint", length = 64, nullable = false)
    private String requestFingerprint;
    @Column(name = "transaction_type", length = 32, nullable = false)
    private String transactionType;
    @Column(name = "currency_id", length = 64, nullable = false)
    private String currencyId;
    @Column(name = "scope_key", length = 128, nullable = false)
    private String scopeKey;
    @Column(name = "actor_player_id")
    private Long actorPlayerId;
    @Column(name = "actor_name", length = 64, nullable = false)
    private String actorName;
    @Column(name = "reason", length = 255, nullable = false)
    private String reason;
    @Column(name = "metadata_json", length = 4096, nullable = false)
    private String metadataJson;
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public Long getId() { return id; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public void setIdempotencyKeyHash(String idempotencyKeyHash) { this.idempotencyKeyHash = idempotencyKeyHash; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getCurrencyId() { return currencyId; }
    public void setCurrencyId(String currencyId) { this.currencyId = currencyId; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public Long getActorPlayerId() { return actorPlayerId; }
    public void setActorPlayerId(Long actorPlayerId) { this.actorPlayerId = actorPlayerId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
