package nl.hauntedmc.serverfeatures.features.lottery.service;

import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.LeaderboardEntry;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PlayerSummary;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.RoundingMode;
import java.util.Map;

/** Builds player and staff views without owning gameplay state. */
final class LotteryViewService {

    private final LotteryService service;
    private final Lottery feature;
    private final LotterySettings settings;
    private final LotteryRepository repository;

    LotteryViewService(
            LotteryService service,
            Lottery feature,
            LotterySettings settings,
            LotteryRepository repository
    ) {
        this.service = service;
        this.feature = feature;
        this.settings = settings;
        this.repository = repository;
    }

    void requestOverview(CommandSender sender) {
        RoundSnapshot round = service.snapshot().orElse(null);
        if (!service.isReady() || round == null) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
        PlayerSummary summary = sender instanceof Player player
                ? service.cachedSummary(player.getUniqueId())
                : null;
        feature.send(sender, "lottery.ui.header");
        feature.send(sender, "lottery.ui.pot", Map.of("pot", service.format(round.grossPot())));
        feature.send(sender, "lottery.ui.draw", Map.of(
                "remaining", formatDuration(round.remainingMillis(service.now())),
                "status", round.paused()
                        ? "paused"
                        : round.status().name().toLowerCase(java.util.Locale.ROOT)
        ));
        feature.send(sender, "lottery.ui.sales", Map.of(
                "tickets", Integer.toString(round.totalTickets()),
                "participants", Integer.toString(round.participants()),
                "price", service.format(round.ticketPrice())
        ));
        if (summary != null) {
            feature.send(sender, "lottery.ui.player", Map.of(
                    "tickets", Integer.toString(summary.tickets()),
                    "odds", summary.odds().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "pending", service.format(summary.pendingPayout())
            ));
        }
        feature.send(sender, "lottery.ui.footer");
    }

    void requestHistory(CommandSender sender, int page) {
        int offset = Math.multiplyExact(page - 1, settings.history().pageSize());
        service.submit(() -> repository.history(
                        settings.lotteryKey(),
                        offset,
                        settings.history().pageSize()
                ))
                .whenComplete((items, failure) -> service.main(() -> {
                    if (failure != null) {
                        feature.send(sender, "lottery.query_failed");
                        service.log("Could not load Lottery history", failure);
                        return;
                    }
                    feature.send(sender, "lottery.history.header", Map.of("page", Integer.toString(page)));
                    if (items.isEmpty()) {
                        feature.send(sender, "lottery.history.empty");
                        return;
                    }
                    for (HistoryItem item : items) {
                        String winners = item.winners().isEmpty()
                                ? "-"
                                : item.winners().stream()
                                .map(winner -> winner.playerName()
                                        + " (" + service.format(winner.amount()) + ")")
                                .collect(java.util.stream.Collectors.joining(", "));
                        feature.send(sender, "lottery.history.entry", Map.of(
                                "round", item.roundId(),
                                "winners", winners,
                                "payout", service.format(item.payout()),
                                "tickets", Integer.toString(item.tickets()),
                                "participants", Integer.toString(item.participants())
                        ));
                    }
                }));
    }

    void requestLeaderboard(CommandSender sender, boolean donations, int page) {
        int offset = Math.multiplyExact(page - 1, settings.history().leaderboardSize());
        service.submit(() -> repository.leaderboard(
                        settings.lotteryKey(),
                        donations,
                        offset,
                        settings.history().leaderboardSize()
                ))
                .whenComplete((entries, failure) -> service.main(() -> {
                    if (failure != null) {
                        feature.send(sender, "lottery.query_failed");
                        service.log("Could not load Lottery leaderboard", failure);
                        return;
                    }
                    feature.send(sender, donations
                            ? "lottery.leaderboard.donations_header"
                            : "lottery.leaderboard.wins_header", Map.of("page", Integer.toString(page)));
                    if (entries.isEmpty()) {
                        feature.send(sender, "lottery.leaderboard.empty");
                        return;
                    }
                    for (LeaderboardEntry entry : entries) {
                        feature.send(sender, "lottery.leaderboard.entry", Map.of(
                                "rank", Integer.toString(entry.rank()),
                                "player", entry.playerName(),
                                "amount", service.format(entry.amount()),
                                "count", Long.toString(entry.count())
                        ));
                    }
                }));
    }

    void requestAdminStatus(CommandSender sender) {
        RoundSnapshot round = service.snapshot().orElse(null);
        if (!service.isReady() || round == null) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
        feature.send(sender, "lottery.admin.status", Map.of(
                "lottery", round.lotteryKey(),
                "round", round.roundId(),
                "status", round.paused() ? "paused" : round.status().name(),
                "pot", service.format(round.grossPot()),
                "tickets", Integer.toString(round.totalTickets()),
                "participants", Integer.toString(round.participants()),
                "remaining", formatDuration(round.remainingMillis(service.now()))
        ));
    }

    static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainingSeconds = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }
}
