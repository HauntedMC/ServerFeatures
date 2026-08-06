package nl.hauntedmc.serverfeatures.features.lottery.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Small immutable read models shared by the Lottery service, commands and placeholders. */
public final class LotteryModels {

    private LotteryModels() {
    }

    public enum RoundStatus {
        OPEN,
        DRAWING,
        COMPLETED,
        CANCELLED
    }

    public enum PayoutStatus {
        PENDING,
        PAYING,
        PAID,
        FAILED
    }

    public enum PayoutKind {
        WIN,
        REFUND
    }

    public record RoundSnapshot(
            String lotteryKey,
            String roundId,
            RoundStatus status,
            long openedAt,
            long closesAt,
            Money ticketPrice,
            Money basePot,
            Money carriedPot,
            Money ticketRevenue,
            Money donations,
            Money adminAdditions,
            Money payoutTotal,
            Money retainedTotal,
            int totalTickets,
            int participants,
            int extensionCount,
            long totalExtensionMillis,
            String seedCommitment,
            String seedReveal,
            boolean paused
    ) {
        public RoundSnapshot {
            Objects.requireNonNull(lotteryKey, "lotteryKey");
            Objects.requireNonNull(roundId, "roundId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(ticketPrice, "ticketPrice");
            Objects.requireNonNull(basePot, "basePot");
            Objects.requireNonNull(carriedPot, "carriedPot");
            Objects.requireNonNull(ticketRevenue, "ticketRevenue");
            Objects.requireNonNull(donations, "donations");
            Objects.requireNonNull(adminAdditions, "adminAdditions");
            Objects.requireNonNull(payoutTotal, "payoutTotal");
            Objects.requireNonNull(retainedTotal, "retainedTotal");
        }

        public Money grossPot() {
            return basePot.add(carriedPot).add(ticketRevenue).add(donations).add(adminAdditions);
        }

        public long remainingMillis(long now) {
            return Math.max(0L, closesAt - now);
        }

        public boolean acceptsEntries(long now) {
            return status == RoundStatus.OPEN && !paused && closesAt > now;
        }
    }

    public record Entry(
            UUID playerUuid,
            Long playerId,
            String playerName,
            int ticketCount,
            Money paidAmount
    ) {
        public Entry {
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(paidAmount, "paidAmount");
            if (ticketCount <= 0) {
                throw new IllegalArgumentException("ticketCount must be positive");
            }
        }
    }

    public record Winner(
            int position,
            UUID playerUuid,
            Long playerId,
            String playerName,
            int ownedTickets,
            long winningTicket,
            Money amount
    ) {
    }

    public record DrawResult(
            String roundId,
            Money grossPot,
            Money payoutTotal,
            Money retainedTotal,
            Money nextCarry,
            int tickets,
            int participants,
            List<Winner> winners,
            String seedCommitment,
            String seedReveal,
            String entryDigest
    ) {
        public DrawResult {
            winners = List.copyOf(winners);
        }
    }

    public record PurchaseReceipt(
            int purchasedTickets,
            int playerTickets,
            int totalTickets,
            int participants,
            Money charged,
            Money pot,
            long closesAt,
            long extensionMillis
    ) {
    }

    public record DonationReceipt(Money amount, Money pot) {
    }

    public record PendingPayout(
            String payoutId,
            UUID playerUuid,
            Money amount,
            PayoutKind kind
    ) {
    }

    public record PlayerSummary(
            UUID playerUuid,
            int tickets,
            BigDecimal odds,
            Money pendingPayout,
            Money totalWon,
            Money totalDonated
    ) {
        public static PlayerSummary empty(UUID playerUuid) {
            return new PlayerSummary(playerUuid, 0, BigDecimal.ZERO, Money.ZERO, Money.ZERO, Money.ZERO);
        }
    }

    public record HistoryItem(
            String roundId,
            long drawnAt,
            Money pot,
            Money payout,
            int tickets,
            int participants,
            List<Winner> winners,
            String seedCommitment,
            String seedReveal,
            String entryDigest
    ) {
        public HistoryItem {
            winners = List.copyOf(winners);
        }
    }

    public record LeaderboardEntry(
            int rank,
            UUID playerUuid,
            String playerName,
            Money amount,
            long count
    ) {
    }
}
