package nl.hauntedmc.serverfeatures.features.lottery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(
        name = "system_lottery_entries",
        indexes = {
                @Index(name = "idx_lottery_entry_round", columnList = "lottery_key,round_id"),
                @Index(name = "idx_lottery_entry_player", columnList = "lottery_key,player_uuid")
        }
)
public class LotteryEntryEntity {

    @Id
    @Column(name = "id", length = 101, nullable = false)
    private String id;

    @Column(name = "lottery_key", length = 64, nullable = false)
    private String lotteryKey;

    @Column(name = "round_id", length = 36, nullable = false)
    private String roundId;

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;

    @Column(name = "player_name", length = 32, nullable = false)
    private String playerName;

    @Column(name = "ticket_count", nullable = false)
    private int ticketCount;

    @Column(name = "paid_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal paidAmount;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

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

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
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

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
