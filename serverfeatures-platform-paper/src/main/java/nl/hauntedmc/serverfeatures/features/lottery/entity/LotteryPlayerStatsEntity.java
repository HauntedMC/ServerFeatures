package nl.hauntedmc.serverfeatures.features.lottery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(
        name = "system_lottery_player_stats",
        indexes = {
                @Index(name = "idx_lottery_stats_won", columnList = "lottery_key,total_won"),
                @Index(name = "idx_lottery_stats_donated", columnList = "lottery_key,total_donated")
        }
)
public class LotteryPlayerStatsEntity {

    @Id
    @Column(name = "id", length = 101, nullable = false)
    private String id;

    @Column(name = "lottery_key", length = 64, nullable = false)
    private String lotteryKey;

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;

    @Column(name = "player_name", length = 32, nullable = false)
    private String playerName;

    @Column(name = "total_spent", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalSpent;

    @Column(name = "total_donated", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalDonated;

    @Column(name = "total_won", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalWon;

    @Column(name = "tickets_bought", nullable = false)
    private long ticketsBought;

    @Column(name = "donation_count", nullable = false)
    private long donationCount;

    @Column(name = "rounds_won", nullable = false)
    private long roundsWon;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLotteryKey() {
        return lotteryKey;
    }

    public void setLotteryKey(String lotteryKey) {
        this.lotteryKey = lotteryKey;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public BigDecimal getTotalDonated() {
        return totalDonated;
    }

    public void setTotalDonated(BigDecimal totalDonated) {
        this.totalDonated = totalDonated;
    }

    public BigDecimal getTotalWon() {
        return totalWon;
    }

    public void setTotalWon(BigDecimal totalWon) {
        this.totalWon = totalWon;
    }

    public long getTicketsBought() {
        return ticketsBought;
    }

    public void setTicketsBought(long ticketsBought) {
        this.ticketsBought = ticketsBought;
    }

    public long getDonationCount() {
        return donationCount;
    }

    public void setDonationCount(long donationCount) {
        this.donationCount = donationCount;
    }

    public long getRoundsWon() {
        return roundsWon;
    }

    public void setRoundsWon(long roundsWon) {
        this.roundsWon = roundsWon;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
