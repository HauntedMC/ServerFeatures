package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "economy_settings")
public class EconomyPlayerSettingsEntity {
    @Id
    @Column(name = "account_id", length = 192, nullable = false)
    private String accountId;
    @Column(name = "payments_enabled", nullable = false)
    private boolean paymentsEnabled;
    @Column(name = "account_status", length = 24, nullable = false)
    private String accountStatus;
    @Column(name = "status_reason", length = 255)
    private String statusReason;
    @Column(name = "status_actor_player_id")
    private Long statusActorPlayerId;
    @Column(name = "last_payment_at")
    private Long lastPaymentAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public boolean isPaymentsEnabled() { return paymentsEnabled; }
    public void setPaymentsEnabled(boolean paymentsEnabled) { this.paymentsEnabled = paymentsEnabled; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public Long getStatusActorPlayerId() { return statusActorPlayerId; }
    public void setStatusActorPlayerId(Long statusActorPlayerId) { this.statusActorPlayerId = statusActorPlayerId; }
    public Long getLastPaymentAt() { return lastPaymentAt; }
    public void setLastPaymentAt(Long lastPaymentAt) { this.lastPaymentAt = lastPaymentAt; }
    public long getVersion() { return version; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
