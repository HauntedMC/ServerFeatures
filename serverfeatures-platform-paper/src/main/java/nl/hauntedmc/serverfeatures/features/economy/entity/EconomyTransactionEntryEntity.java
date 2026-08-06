package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "system_economy_transaction_entry",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_economy_entry_role",
                        columnNames = {"transaction_id", "entry_role"}
                ),
                @UniqueConstraint(
                        name = "uq_economy_entry_account",
                        columnNames = {"transaction_id", "account_id"}
                )
        },
        indexes = {
                @Index(name = "idx_economy_entry_transaction", columnList = "transaction_id"),
                @Index(name = "idx_economy_entry_account", columnList = "account_id,transaction_id")
        }
)
public class EconomyTransactionEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "transaction_id", nullable = false)
    private long transactionId;
    @Column(name = "account_id", length = 192, nullable = false)
    private String accountId;
    @Column(name = "player_id", nullable = false)
    private long playerId;
    @Column(name = "entry_role", length = 24, nullable = false)
    private String entryRole;
    @Column(name = "delta", precision = 38, scale = 8, nullable = false)
    private BigDecimal delta;
    @Column(name = "balance_before", precision = 38, scale = 8, nullable = false)
    private BigDecimal balanceBefore;
    @Column(name = "balance_after", precision = 38, scale = 8, nullable = false)
    private BigDecimal balanceAfter;

    public Long getId() { return id; }
    public long getTransactionId() { return transactionId; }
    public void setTransactionId(long transactionId) { this.transactionId = transactionId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public String getEntryRole() { return entryRole; }
    public void setEntryRole(String entryRole) { this.entryRole = entryRole; }
    public BigDecimal getDelta() { return delta; }
    public void setDelta(BigDecimal delta) { this.delta = delta; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public void setBalanceBefore(BigDecimal balanceBefore) { this.balanceBefore = balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
}
