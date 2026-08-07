package nl.hauntedmc.serverfeatures.features.lottery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A reserved ticket purchase whose payment is driven by the Economy durable-workflow outbox.
 *
 * <p>The identifier is also the Economy workflow key.  It therefore provides the stable business
 * identity needed to make retries, restarts, and compensating refunds safe.</p>
 */
@Entity
@Table(
        name = "lottery_purchase_intents",
        indexes = {
                @Index(name = "idx_lottery_purchase_intent_round", columnList = "lottery_key,round_id,state"),
                @Index(name = "idx_lottery_purchase_intent_player", columnList = "lottery_key,player_uuid,state")
        }
)
public class LotteryPurchaseIntentEntity {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "lottery_key", length = 64, nullable = false)
    private String lotteryKey;

    @Column(name = "round_id", length = 36, nullable = false)
    private String roundId;

    @Column(name = "player_id", nullable = false)
    private long playerId;

    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;

    @Column(name = "player_name", length = 32, nullable = false)
    private String playerName;

    @Column(name = "ticket_count", nullable = false)
    private int ticketCount;

    @Column(name = "charged_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal chargedAmount;

    @Column(name = "state", length = 24, nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLotteryKey() { return lotteryKey; }
    public void setLotteryKey(String lotteryKey) { this.lotteryKey = lotteryKey; }
    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }
    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getTicketCount() { return ticketCount; }
    public void setTicketCount(int ticketCount) { this.ticketCount = ticketCount; }
    public BigDecimal getChargedAmount() { return chargedAmount; }
    public void setChargedAmount(BigDecimal chargedAmount) { this.chargedAmount = chargedAmount; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
