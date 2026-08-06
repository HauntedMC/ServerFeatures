package nl.hauntedmc.serverfeatures.features.lottery.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PlayerSummary;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.RoundingMode;
import java.util.Locale;

/** PlaceholderAPI expansion backed only by the Lottery cache. */
public final class LotteryPlaceholder extends PlaceholderExpansion {

    private final Lottery feature;

    public LotteryPlaceholder(Lottery feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lottery";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HauntedMC";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (feature.service() == null) {
            return "available".equalsIgnoreCase(params.trim()) ? "false" : "0";
        }
        RoundSnapshot round = feature.service().snapshot().orElse(null);
        String key = params.trim().toLowerCase(Locale.ROOT);
        if (round == null) {
            return switch (key) {
                case "available" -> "false";
                case "status" -> "unavailable";
                default -> "0";
            };
        }
        PlayerSummary summary = player == null
                ? null
                : feature.service().cachedSummary(player.getUniqueId());
        return switch (key) {
            case "available" -> "true";
            case "lottery_key" -> round.lotteryKey();
            case "status" -> round.paused() ? "paused" : round.status().name().toLowerCase(Locale.ROOT);
            case "round_id" -> round.roundId();
            case "pot" -> round.grossPot().plain();
            case "ticket_price" -> round.ticketPrice().plain();
            case "tickets_sold" -> Integer.toString(round.totalTickets());
            case "participants" -> Integer.toString(round.participants());
            case "time_remaining" -> feature.service().formatDuration(
                    round.remainingMillis(System.currentTimeMillis())
            );
            case "next_draw_epoch" -> Long.toString(round.closesAt());
            case "seed_commitment" -> round.seedCommitment();
            case "player_tickets" -> summary == null ? "0" : Integer.toString(summary.tickets());
            case "player_odds" -> summary == null
                    ? "0.00"
                    : summary.odds().setScale(2, RoundingMode.HALF_UP).toPlainString();
            case "player_pending_payout" -> summary == null ? "0.00" : summary.pendingPayout().plain();
            case "player_total_won" -> summary == null ? "0.00" : summary.totalWon().plain();
            case "player_total_donated" -> summary == null ? "0.00" : summary.totalDonated().plain();
            default -> null;
        };
    }
}
