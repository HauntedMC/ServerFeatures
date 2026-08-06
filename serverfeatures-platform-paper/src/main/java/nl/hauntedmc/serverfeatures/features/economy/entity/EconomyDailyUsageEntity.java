package nl.hauntedmc.serverfeatures.features.economy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "player_economy_daily_usage")
public class EconomyDailyUsageEntity {
    @Id
    @Column(name = "id", length = 224, nullable = false)
    private String id;
    @Column(name = "account_id", length = 192, nullable = false)
    private String accountId;
    @Column(name = "usage_date", length = 10, nullable = false)
    private String usageDate;
    @Column(name = "sent_amount", precision = 38, scale = 8, nullable = false)
    private BigDecimal sentAmount;
    @Column(name = "received_amount", precision = 38, scale = 8, nullable = false)
    private BigDecimal receivedAmount;
    @Column(name = "sent_count", nullable = false)
    private int sentCount;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getUsageDate() { return usageDate; }
    public void setUsageDate(String usageDate) { this.usageDate = usageDate; }
    public BigDecimal getSentAmount() { return sentAmount; }
    public void setSentAmount(BigDecimal sentAmount) { this.sentAmount = sentAmount; }
    public BigDecimal getReceivedAmount() { return receivedAmount; }
    public void setReceivedAmount(BigDecimal receivedAmount) { this.receivedAmount = receivedAmount; }
    public int getSentCount() { return sentCount; }
    public void setSentCount(int sentCount) { this.sentCount = sentCount; }
    public long getVersion() { return version; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
