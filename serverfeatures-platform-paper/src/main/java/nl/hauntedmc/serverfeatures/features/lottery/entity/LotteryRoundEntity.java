package nl.hauntedmc.serverfeatures.features.lottery.entity;

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
        name = "system_lottery_rounds",
        indexes = {
                @Index(name = "idx_lottery_round_status", columnList = "lottery_key,status,opened_at"),
                @Index(name = "idx_lottery_round_history", columnList = "lottery_key,drawn_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lottery_active_round",
                columnNames = "active_key"
        )
)
public class LotteryRoundEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "lottery_key", length = 64, nullable = false)
    private String lotteryKey;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "active_key", length = 64)
    private String activeKey;

    @Column(name = "opened_at", nullable = false)
    private long openedAt;

    @Column(name = "closes_at", nullable = false)
    private long closesAt;

    @Column(name = "drawn_at", nullable = false)
    private long drawnAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "ticket_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal ticketPrice;

    @Column(name = "base_pot", precision = 19, scale = 2, nullable = false)
    private BigDecimal basePot;

    @Column(name = "carried_pot", precision = 19, scale = 2, nullable = false)
    private BigDecimal carriedPot;

    @Column(name = "ticket_revenue", precision = 19, scale = 2, nullable = false)
    private BigDecimal ticketRevenue;

    @Column(name = "donations", precision = 19, scale = 2, nullable = false)
    private BigDecimal donations;

    @Column(name = "admin_additions", precision = 19, scale = 2, nullable = false)
    private BigDecimal adminAdditions;

    @Column(name = "payout_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal payoutTotal;

    @Column(name = "retained_total", precision = 19, scale = 2, nullable = false)
    private BigDecimal retainedTotal;

    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    @Column(name = "participants", nullable = false)
    private int participants;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "total_extension_millis", nullable = false)
    private long totalExtensionMillis;

    @Column(name = "seed_commitment", length = 64, nullable = false)
    private String seedCommitment;

    @Column(name = "seed_reveal", length = 64, nullable = false)
    private String seedReveal;

    @Column(name = "entry_digest", length = 64)
    private String entryDigest;

    @Column(name = "paused", nullable = false)
    private boolean paused;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(long openedAt) {
        this.openedAt = openedAt;
    }

    public long getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(long closesAt) {
        this.closesAt = closesAt;
    }

    public long getDrawnAt() {
        return drawnAt;
    }

    public void setDrawnAt(long drawnAt) {
        this.drawnAt = drawnAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(BigDecimal ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public BigDecimal getBasePot() {
        return basePot;
    }

    public void setBasePot(BigDecimal basePot) {
        this.basePot = basePot;
    }

    public BigDecimal getCarriedPot() {
        return carriedPot;
    }

    public void setCarriedPot(BigDecimal carriedPot) {
        this.carriedPot = carriedPot;
    }

    public BigDecimal getTicketRevenue() {
        return ticketRevenue;
    }

    public void setTicketRevenue(BigDecimal ticketRevenue) {
        this.ticketRevenue = ticketRevenue;
    }

    public BigDecimal getDonations() {
        return donations;
    }

    public void setDonations(BigDecimal donations) {
        this.donations = donations;
    }

    public BigDecimal getAdminAdditions() {
        return adminAdditions;
    }

    public void setAdminAdditions(BigDecimal adminAdditions) {
        this.adminAdditions = adminAdditions;
    }

    public BigDecimal getPayoutTotal() {
        return payoutTotal;
    }

    public void setPayoutTotal(BigDecimal payoutTotal) {
        this.payoutTotal = payoutTotal;
    }

    public BigDecimal getRetainedTotal() {
        return retainedTotal;
    }

    public void setRetainedTotal(BigDecimal retainedTotal) {
        this.retainedTotal = retainedTotal;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(int totalTickets) {
        this.totalTickets = totalTickets;
    }

    public int getParticipants() {
        return participants;
    }

    public void setParticipants(int participants) {
        this.participants = participants;
    }

    public int getExtensionCount() {
        return extensionCount;
    }

    public void setExtensionCount(int extensionCount) {
        this.extensionCount = extensionCount;
    }

    public long getTotalExtensionMillis() {
        return totalExtensionMillis;
    }

    public void setTotalExtensionMillis(long totalExtensionMillis) {
        this.totalExtensionMillis = totalExtensionMillis;
    }

    public String getSeedCommitment() {
        return seedCommitment;
    }

    public void setSeedCommitment(String seedCommitment) {
        this.seedCommitment = seedCommitment;
    }

    public String getSeedReveal() {
        return seedReveal;
    }

    public void setSeedReveal(String seedReveal) {
        this.seedReveal = seedReveal;
    }

    public String getEntryDigest() {
        return entryDigest;
    }

    public void setEntryDigest(String entryDigest) {
        this.entryDigest = entryDigest;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public long getVersion() {
        return version;
    }
}
