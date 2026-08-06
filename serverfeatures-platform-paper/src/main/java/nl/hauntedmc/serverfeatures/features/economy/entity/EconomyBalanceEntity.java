package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(
        name = "player_economy_balance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_economy_balance_account",
                        columnNames = {"player_id", "currency_id", "scope_key"}
                ),
                @UniqueConstraint(
                        name = "uq_economy_balance_uuid_account",
                        columnNames = {"player_uuid", "currency_id", "scope_key"}
                )
        },
        indexes = {
                @Index(name = "idx_economy_balance_top", columnList = "currency_id,scope_key,balance"),
                @Index(name = "idx_economy_balance_player_scope", columnList = "player_id,scope_key")
        }
)
public class EconomyBalanceEntity {
    @Id
    @Column(name = "id", length = 192, nullable = false)
    private String id;
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
    @Column(name = "balance", precision = 38, scale = 8, nullable = false)
    private BigDecimal balance;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public long getVersion() { return version; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
