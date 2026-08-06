package nl.hauntedmc.serverfeatures.features.lottery.service;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.draw.LotteryDrawEngine;
import nl.hauntedmc.serverfeatures.features.lottery.economy.LotteryEconomy;
import nl.hauntedmc.serverfeatures.features.lottery.economy.LotteryEconomy.EconomyResult;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.DrawResult;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.LeaderboardEntry;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PendingPayout;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PayoutStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PlayerSummary;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PurchaseReceipt;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository.PreparedDraw;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/** Coordinates the Lottery gameplay flow while keeping Vault on the main thread. */
public final class LotteryService {

    private static final long ROUND_REFRESH_INTERVAL_MILLIS = 30_000L;

    private final Lottery feature;
    private final LotterySettings settings;
    private final LotteryRepository repository;
    private final LotteryEconomy economy;
    private final LotteryDrawEngine drawEngine;
    private final PlayerIdentityResolver identityResolver;
    private final AtomicReference<RoundSnapshot> snapshot = new AtomicReference<>();
    private final AtomicReference<List<HistoryItem>> recentHistory = new AtomicReference<>(List.of());
    private final Map<UUID, PlayerSummary> playerSummaries = new ConcurrentHashMap<>();
    private final Set<UUID> activePlayerOperations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean drawing = new AtomicBoolean();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Long> announcedThresholds = new HashSet<>();
    private String announcedRoundId;
    private long lastRoundRefresh;

    public LotteryService(
            Lottery feature,
            LotterySettings settings,
            LotteryRepository repository,
            LotteryEconomy economy,
            LotteryDrawEngine drawEngine
    ) {
        this.feature = feature;
        this.settings = settings;
        this.repository = repository;
        this.economy = economy;
        this.drawEngine = drawEngine;
        this.identityResolver = new PlayerIdentityResolver(feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Lottery")));
    }

    public void start() {
        String seed = drawEngine.newSeed();
        submit(() -> repository.ensureOpenRound(settings, seed, drawEngine.commitment(seed), now()))
                .whenComplete((round, failure) -> main(() -> {
                    if (failure != null) {
                        feature.getLogger().log(Level.SEVERE, "Could not initialize Lottery", unwrap(failure));
                        return;
                    }
                    ready.set(true);
                    updateSnapshot(round);
                    refreshHistory();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        refreshPlayerSummary(player.getUniqueId());
                    }
                }));
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::tick,
                BukkitTime.ticks(20L),
                BukkitTime.ticks(20L)
        );
    }

    public void close() {
        closed.set(true);
        ready.set(false);
        drawing.set(false);
        snapshot.set(null);
        recentHistory.set(List.of());
        playerSummaries.clear();
        activePlayerOperations.clear();
        announcedThresholds.clear();
    }

    public boolean isReady() {
        return ready.get() && !closed.get();
    }

    public Optional<RoundSnapshot> snapshot() {
        return Optional.ofNullable(snapshot.get());
    }

    public PlayerSummary cachedSummary(UUID playerUuid) {
        return playerSummaries.getOrDefault(playerUuid, PlayerSummary.empty(playerUuid));
    }

    public List<HistoryItem> recentHistory() {
        return recentHistory.get();
    }

    public String format(Money amount) {
        return economy.format(amount);
    }

    public Money balance(OfflinePlayer player) {
        return economy.balance(player);
    }

    public int maximumAffordable(Player player) {
        RoundSnapshot round = snapshot.get();
        if (!isReady() || round == null || !round.acceptsEntries(now())) {
            return 0;
        }
        int affordable = balance(player).amount()
                .divide(round.ticketPrice().amount(), 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(settings.tickets().maximumPerCommand()))
                .intValue();
        PlayerSummary summary = cachedSummary(player.getUniqueId());
        if (settings.tickets().maximumPerPlayer() > 0) {
            affordable = Math.min(
                    affordable,
                    Math.max(0, settings.tickets().maximumPerPlayer() - summary.tickets())
            );
        }
        if (settings.tickets().maximumPerRound() > 0) {
            affordable = Math.min(
                    affordable,
                    Math.max(0, settings.tickets().maximumPerRound() - round.totalTickets())
            );
        }
        return Math.max(0, affordable);
    }

    public void purchase(Player player, int ticketCount) {
        requireMainThread();
        RoundSnapshot round = snapshot.get();
        if (!isReady() || round == null) {
            feature.send(player, "lottery.unavailable");
            return;
        }
        if (!round.acceptsEntries(now())) {
            feature.send(player, round.paused() ? "lottery.paused" : "lottery.closed");
            return;
        }
        if (ticketCount < 1 || ticketCount > settings.tickets().maximumPerCommand()) {
            feature.send(player, "lottery.buy.invalid_amount", Map.of(
                    "maximum", Integer.toString(settings.tickets().maximumPerCommand())
            ));
            return;
        }
        if (!beginPlayerOperation(player)) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Money cost = round.ticketPrice().multiply(ticketCount);
        if (balance(player).compareTo(cost) < 0) {
            endPlayerOperation(playerUuid);
            feature.send(player, "lottery.buy.insufficient", Map.of(
                    "cost", format(cost),
                    "balance", format(balance(player))
            ));
            return;
        }
        resolveIdentity(playerUuid, identity -> withdrawAndStorePurchase(
                playerUuid,
                playerName,
                identity.playerId(),
                round.roundId(),
                ticketCount,
                cost
        ));
    }

    public void donate(Player player, Money amount) {
        requireMainThread();
        RoundSnapshot round = snapshot.get();
        if (!isReady() || round == null) {
            feature.send(player, "lottery.unavailable");
            return;
        }
        if (!settings.pot().donationsEnabled()) {
            feature.send(player, "lottery.donate.disabled");
            return;
        }
        if (amount.compareTo(settings.pot().minimumDonation()) < 0) {
            feature.send(player, "lottery.donate.minimum", Map.of(
                    "minimum", format(settings.pot().minimumDonation())
            ));
            return;
        }
        if (!round.acceptsEntries(now())) {
            feature.send(player, round.paused() ? "lottery.paused" : "lottery.closed");
            return;
        }
        if (balance(player).compareTo(amount) < 0) {
            feature.send(player, "lottery.donate.insufficient", Map.of(
                    "amount", format(amount),
                    "balance", format(balance(player))
            ));
            return;
        }
        if (!beginPlayerOperation(player)) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        resolveIdentity(playerUuid, identity -> withdrawAndStoreDonation(
                playerUuid,
                playerName,
                identity.playerId(),
                round.roundId(),
                amount
        ));
    }

    public void claim(Player player, boolean automatic) {
        requireMainThread();
        if (!isReady()) {
            if (!automatic) {
                feature.send(player, "lottery.unavailable");
            }
            return;
        }
        if (!automatic && !settings.payouts().claimCommandEnabled()) {
            feature.send(player, "lottery.claim.disabled");
            return;
        }
        if (!beginPlayerOperation(player)) {
            return;
        }
        claimNext(player.getUniqueId(), automatic, Money.ZERO);
    }

    public void onJoin(Player player) {
        refreshPlayerSummary(player.getUniqueId());
        if (settings.payouts().automaticOnJoin()) {
            claim(player, true);
        }
    }

    public void onQuit(Player player) {
        activePlayerOperations.remove(player.getUniqueId());
        playerSummaries.remove(player.getUniqueId());
    }

    public void requestOverview(CommandSender sender) {
        RoundSnapshot round = snapshot.get();
        if (!isReady() || round == null) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
        PlayerSummary summary = sender instanceof Player player
                ? cachedSummary(player.getUniqueId())
                : null;
        feature.send(sender, "lottery.ui.header");
        feature.send(sender, "lottery.ui.pot", Map.of("pot", format(round.grossPot())));
        feature.send(sender, "lottery.ui.draw", Map.of(
                "remaining", formatDuration(round.remainingMillis(now())),
                "status", round.paused() ? "paused" : round.status().name().toLowerCase(java.util.Locale.ROOT)
        ));
        feature.send(sender, "lottery.ui.sales", Map.of(
                "tickets", Integer.toString(round.totalTickets()),
                "participants", Integer.toString(round.participants()),
                "price", format(round.ticketPrice())
        ));
        if (summary != null) {
            feature.send(sender, "lottery.ui.player", Map.of(
                    "tickets", Integer.toString(summary.tickets()),
                    "odds", summary.odds().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "pending", format(summary.pendingPayout())
            ));
        }
        feature.send(sender, "lottery.ui.footer");
    }

    public void requestHistory(CommandSender sender, int page) {
        int offset = Math.multiplyExact(page - 1, settings.history().pageSize());
        submit(() -> repository.history(settings.lotteryKey(), offset, settings.history().pageSize()))
                .whenComplete((items, failure) -> main(() -> {
                    if (failure != null) {
                        feature.send(sender, "lottery.query_failed");
                        log("Could not load Lottery history", failure);
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
                                .map(winner -> winner.playerName() + " (" + format(winner.amount()) + ")")
                                .collect(java.util.stream.Collectors.joining(", "));
                        feature.send(sender, "lottery.history.entry", Map.of(
                                "round", item.roundId(),
                                "winners", winners,
                                "payout", format(item.payout()),
                                "tickets", Integer.toString(item.tickets()),
                                "participants", Integer.toString(item.participants())
                        ));
                    }
                }));
    }

    public void requestLeaderboard(CommandSender sender, boolean donations, int page) {
        int offset = Math.multiplyExact(page - 1, settings.history().leaderboardSize());
        submit(() -> repository.leaderboard(
                        settings.lotteryKey(),
                        donations,
                        offset,
                        settings.history().leaderboardSize()
                ))
                .whenComplete((entries, failure) -> main(() -> {
                    if (failure != null) {
                        feature.send(sender, "lottery.query_failed");
                        log("Could not load Lottery leaderboard", failure);
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
                                "amount", format(entry.amount()),
                                "count", Long.toString(entry.count())
                        ));
                    }
                }));
    }

    public void requestAdminStatus(CommandSender sender) {
        RoundSnapshot round = snapshot.get();
        if (round == null) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
        feature.send(sender, "lottery.admin.status", Map.of(
                "lottery", round.lotteryKey(),
                "round", round.roundId(),
                "status", round.paused() ? "paused" : round.status().name(),
                "pot", format(round.grossPot()),
                "tickets", Integer.toString(round.totalTickets()),
                "participants", Integer.toString(round.participants()),
                "remaining", formatDuration(round.remainingMillis(now()))
        ));
    }

    public void setPaused(CommandSender sender, boolean paused) {
        submit(() -> repository.setPaused(settings.lotteryKey(), paused, now()))
                .whenComplete((round, failure) -> main(() -> {
                    if (failure != null) {
                        actionFailed(sender, failure);
                        return;
                    }
                    updateSnapshot(round);
                    feature.send(sender, paused ? "lottery.admin.paused" : "lottery.admin.resumed");
                }));
    }

    public void addToPot(CommandSender sender, Money amount) {
        submit(() -> repository.addPot(settings.lotteryKey(), amount, now()))
                .whenComplete((round, failure) -> main(() -> {
                    if (failure != null) {
                        actionFailed(sender, failure);
                        return;
                    }
                    updateSnapshot(round);
                    feature.send(sender, "lottery.admin.pot_added", Map.of(
                            "amount", format(amount),
                            "pot", format(round.grossPot())
                    ));
                }));
    }

    public void forceDraw(CommandSender sender) {
        if (!isReady()) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
        feature.send(sender, "lottery.admin.draw_started");
        draw(true);
    }

    public void cancelRound(CommandSender sender) {
        String seed = drawEngine.newSeed();
        submit(() -> repository.cancelRound(
                        settings,
                        seed,
                        drawEngine.commitment(seed),
                        now()
                ))
                .whenComplete((cancelled, failure) -> main(() -> {
                    if (failure != null) {
                        actionFailed(sender, failure);
                        return;
                    }
                    updateSnapshot(cancelled.nextRound());
                    refreshHistory();
                    feature.broadcast("lottery.broadcast.cancelled", Map.of(
                            "refunds", format(cancelled.refunds())
                    ));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        refreshPlayerSummary(player.getUniqueId());
                        if (settings.payouts().automaticOnJoin()) {
                            claim(player, true);
                        }
                    }
                }));
    }

    public String formatDuration(long millis) {
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

    private void withdrawAndStorePurchase(
            UUID playerUuid,
            String playerName,
            Long playerId,
            String roundId,
            int ticketCount,
            Money cost
    ) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline() || !isReady()) {
            endPlayerOperation(playerUuid);
            return;
        }
        EconomyResult withdrawal = economy.withdraw(player, cost);
        if (!withdrawal.successful()) {
            endPlayerOperation(playerUuid);
            feature.send(player, withdrawal.uncertain() ? "lottery.transaction.uncertain" : "lottery.buy.withdraw_failed", Map.of(
                    "reason", withdrawal.message()
            ));
            return;
        }
        submit(() -> repository.purchase(
                        settings,
                        roundId,
                        playerUuid,
                        playerId,
                        playerName,
                        ticketCount,
                        cost,
                        now()
                ))
                .whenComplete((receipt, failure) -> main(() -> {
                    endPlayerOperation(playerUuid);
                    if (failure != null) {
                        refund(player, cost, "purchase", failure);
                        return;
                    }
                    updateFromReceipt(receipt);
                    refreshPlayerSummary(playerUuid);
                    feature.send(player, "lottery.buy.success", Map.of(
                            "tickets", Integer.toString(receipt.purchasedTickets()),
                            "cost", format(receipt.charged()),
                            "player_tickets", Integer.toString(receipt.playerTickets()),
                            "pot", format(receipt.pot())
                    ));
                }));
    }

    private void withdrawAndStoreDonation(
            UUID playerUuid,
            String playerName,
            Long playerId,
            String roundId,
            Money amount
    ) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline() || !isReady()) {
            endPlayerOperation(playerUuid);
            return;
        }
        EconomyResult withdrawal = economy.withdraw(player, amount);
        if (!withdrawal.successful()) {
            endPlayerOperation(playerUuid);
            feature.send(player, withdrawal.uncertain()
                    ? "lottery.transaction.uncertain"
                    : "lottery.donate.withdraw_failed", Map.of("reason", withdrawal.message()));
            return;
        }
        submit(() -> repository.donate(
                        settings,
                        roundId,
                        playerUuid,
                        playerId,
                        playerName,
                        amount,
                        now()
                ))
                .whenComplete((receipt, failure) -> main(() -> {
                    endPlayerOperation(playerUuid);
                    if (failure != null) {
                        refund(player, amount, "donation", failure);
                        return;
                    }
                    refreshRound();
                    refreshPlayerSummary(playerUuid);
                    feature.send(player, "lottery.donate.success", Map.of(
                            "amount", format(receipt.amount()),
                            "pot", format(receipt.pot())
                    ));
                }));
    }

    private void refund(Player player, Money amount, String transaction, Throwable failure) {
        EconomyResult refund = economy.deposit(player, amount);
        if (refund.successful()) {
            feature.send(player, "lottery.transaction.refunded", Map.of("amount", format(amount)));
        } else {
            feature.send(player, "lottery.transaction.uncertain");
        }
        log("Lottery " + transaction + " could not be stored; refund result: " + refund.message(), failure);
    }

    private void claimNext(UUID playerUuid, boolean automatic, Money paid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline() || !isReady()) {
            endPlayerOperation(playerUuid);
            return;
        }
        submit(() -> repository.reserveNextPayout(settings.lotteryKey(), playerUuid, now()))
                .whenComplete((optional, failure) -> main(() -> {
                    if (failure != null) {
                        endPlayerOperation(playerUuid);
                        if (!automatic) {
                            feature.send(player, "lottery.claim.failed");
                        }
                        log("Could not reserve Lottery payout", failure);
                        return;
                    }
                    if (optional.isEmpty()) {
                        endPlayerOperation(playerUuid);
                        refreshPlayerSummary(playerUuid);
                        if (paid.isPositive()) {
                            feature.send(player, "lottery.claim.success", Map.of("amount", format(paid)));
                        } else if (!automatic) {
                            feature.send(player, "lottery.claim.none");
                        }
                        return;
                    }
                    deliverPayout(player, optional.get(), automatic, paid);
                }));
    }

    private void deliverPayout(Player player, PendingPayout payout, boolean automatic, Money paid) {
        EconomyResult result = economy.deposit(player, payout.amount());
        PayoutStatus status = result.successful()
                ? PayoutStatus.PAID
                : result.uncertain() ? PayoutStatus.FAILED : PayoutStatus.PENDING;
        submit(() -> {
            repository.finishPayout(
                    settings.lotteryKey(),
                    payout.payoutId(),
                    status,
                    result.successful() ? null : result.message(),
                    now()
            );
            return null;
        }).whenComplete((ignored, failure) -> main(() -> {
            if (failure != null) {
                endPlayerOperation(player.getUniqueId());
                feature.send(player, "lottery.transaction.uncertain");
                log("Could not save Lottery payout state " + payout.payoutId(), failure);
                return;
            }
            if (!result.successful()) {
                endPlayerOperation(player.getUniqueId());
                if (!automatic) {
                    feature.send(player, result.uncertain()
                            ? "lottery.transaction.uncertain"
                            : "lottery.claim.payout_failed", Map.of("reason", result.message()));
                }
                refreshPlayerSummary(player.getUniqueId());
                return;
            }
            claimNext(player.getUniqueId(), automatic, paid.add(payout.amount()));
        }));
    }

    private void tick() {
        if (!isReady()) {
            return;
        }
        long currentTime = now();
        RoundSnapshot round = snapshot.get();
        if (round == null) {
            return;
        }
        if (currentTime - lastRoundRefresh >= ROUND_REFRESH_INTERVAL_MILLIS) {
            lastRoundRefresh = currentTime;
            refreshRound();
        }
        if (!round.roundId().equals(announcedRoundId)) {
            announcedRoundId = round.roundId();
            announcedThresholds.clear();
        }
        announceRemaining(round, currentTime);
        if (!round.paused() && round.status() == RoundStatus.OPEN && round.closesAt() <= currentTime) {
            draw(false);
        }
    }

    private void draw(boolean force) {
        if (!isReady() || !drawing.compareAndSet(false, true)) {
            return;
        }
        submit(() -> repository.prepareDraw(settings.lotteryKey(), force, now()))
                .whenComplete((prepared, prepareFailure) -> {
                    if (prepareFailure != null) {
                        drawing.set(false);
                        if (force) {
                            log("Could not prepare Lottery draw", prepareFailure);
                        }
                        return;
                    }
                    completeDraw(prepared);
                });
    }

    private void completeDraw(PreparedDraw prepared) {
        submit(() -> {
            DrawResult result = drawEngine.draw(prepared.round(), prepared.entries(), settings);
            String nextSeed = drawEngine.newSeed();
            RoundSnapshot nextRound = repository.completeDraw(
                    settings,
                    prepared,
                    result,
                    nextSeed,
                    drawEngine.commitment(nextSeed),
                    now()
            );
            return new DrawOutcome(result, nextRound);
        }).whenComplete((outcome, failure) -> main(() -> {
            drawing.set(false);
            if (failure != null) {
                submit(() -> {
                    repository.releaseDraw(settings.lotteryKey(), prepared.round().roundId(), now());
                    return null;
                });
                feature.broadcast("lottery.broadcast.draw_failed", Map.of());
                log("Lottery draw failed", failure);
                refreshRound();
                return;
            }
            updateSnapshot(outcome.nextRound());
            refreshHistory();
            broadcastDraw(outcome.result());
            for (var winner : outcome.result().winners()) {
                refreshPlayerSummary(winner.playerUuid());
                Player online = Bukkit.getPlayer(winner.playerUuid());
                if (online != null && settings.payouts().automaticOnJoin()) {
                    claim(online, true);
                }
            }
        }));
    }

    private void broadcastDraw(DrawResult result) {
        if (result.tickets() == 0) {
            feature.broadcast("lottery.broadcast.no_tickets", Map.of(
                    "carry", format(result.nextCarry())
            ));
            return;
        }
        feature.broadcast("lottery.broadcast.draw_header", Map.of(
                "pot", format(result.grossPot()),
                "tickets", Integer.toString(result.tickets()),
                "participants", Integer.toString(result.participants())
        ));
        if (result.winners().isEmpty()) {
            feature.broadcast("lottery.broadcast.no_payout", Map.of(
                    "carry", format(result.nextCarry())
            ));
            return;
        }
        for (var winner : result.winners()) {
            feature.broadcast("lottery.broadcast.winner", Map.of(
                    "position", Integer.toString(winner.position()),
                    "player", winner.playerName(),
                    "amount", format(winner.amount())
            ));
        }
        feature.broadcast("lottery.broadcast.proof", Map.of(
                "round", result.roundId(),
                "commitment", result.seedCommitment(),
                "seed", result.seedReveal(),
                "entry_digest", result.entryDigest()
        ));
    }

    private void announceRemaining(RoundSnapshot round, long currentTime) {
        if (!settings.broadcasts().enabled() || round.paused()) {
            return;
        }
        long remaining = round.remainingMillis(currentTime);
        for (var threshold : settings.broadcasts().remainingTimes()) {
            long millis = threshold.toMillis();
            if (remaining <= millis && announcedThresholds.add(millis)) {
                feature.broadcast("lottery.broadcast.remaining", Map.of(
                        "remaining", formatDuration(remaining),
                        "pot", format(round.grossPot())
                ));
                break;
            }
        }
    }

    private void refreshRound() {
        String seed = drawEngine.newSeed();
        submit(() -> repository.ensureOpenRound(
                        settings,
                        seed,
                        drawEngine.commitment(seed),
                        now()
                ))
                .whenComplete((round, failure) -> {
                    if (failure == null && round != null && !closed.get()) {
                        main(() -> updateSnapshot(round));
                    } else if (failure != null) {
                        log("Could not refresh Lottery round", failure);
                    }
                });
    }

    private void refreshHistory() {
        submit(() -> repository.history(settings.lotteryKey(), 0, settings.history().pageSize()))
                .whenComplete((history, failure) -> {
                    if (failure == null && history != null && !closed.get()) {
                        recentHistory.set(List.copyOf(history));
                    }
                });
    }

    private void refreshPlayerSummary(UUID playerUuid) {
        if (!isReady()) {
            return;
        }
        submit(() -> repository.playerSummary(settings.lotteryKey(), playerUuid))
                .whenComplete((summary, failure) -> {
                    if (failure == null && summary != null && !closed.get()) {
                        playerSummaries.put(playerUuid, summary);
                    }
                });
    }

    private void updateSnapshot(RoundSnapshot round) {
        snapshot.set(round);
    }

    private void updateFromReceipt(PurchaseReceipt receipt) {
        RoundSnapshot round = snapshot.get();
        if (round == null) {
            refreshRound();
            return;
        }
        snapshot.set(new RoundSnapshot(
                round.lotteryKey(),
                round.roundId(),
                round.status(),
                round.openedAt(),
                receipt.closesAt(),
                round.ticketPrice(),
                round.basePot(),
                round.carriedPot(),
                round.ticketRevenue().add(receipt.charged()),
                round.donations(),
                round.adminAdditions(),
                round.payoutTotal(),
                round.retainedTotal(),
                receipt.totalTickets(),
                receipt.participants(),
                round.extensionCount() + (receipt.extensionMillis() > 0L ? 1 : 0),
                round.totalExtensionMillis() + receipt.extensionMillis(),
                round.seedCommitment(),
                round.seedReveal(),
                round.paused()
        ));
    }

    private void resolveIdentity(UUID playerUuid, java.util.function.Consumer<PlayerIdentity> consumer) {
        identityResolver.whenReady(playerUuid).whenComplete((identity, failure) -> main(() -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                endPlayerOperation(playerUuid);
                return;
            }
            if (failure != null || identity == null || identity.isEmpty()) {
                endPlayerOperation(playerUuid);
                feature.send(player, "lottery.identity_unavailable");
                if (failure != null) {
                    log("Could not resolve Lottery identity for " + playerUuid, failure);
                }
                return;
            }
            consumer.accept(identity.get());
        }));
    }

    private boolean beginPlayerOperation(Player player) {
        if (activePlayerOperations.add(player.getUniqueId())) {
            return true;
        }
        feature.send(player, "lottery.processing_existing");
        return false;
    }

    private void endPlayerOperation(UUID playerUuid) {
        activePlayerOperations.remove(playerUuid);
    }

    private void actionFailed(CommandSender sender, Throwable failure) {
        feature.send(sender, "lottery.admin.action_failed", Map.of("reason", rootMessage(failure)));
        log("Lottery administrator action failed", failure);
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> supplier) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Lottery is closed"));
        }
        return feature.getLifecycleManager().getTaskManager().supplyAsync(supplier);
    }

    private void main(Runnable task) {
        if (closed.get()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(task);
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Lottery player operations must run on the Paper main thread");
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private void log(String message, Throwable failure) {
        feature.getLogger().log(Level.WARNING, message, unwrap(failure));
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = unwrap(failure);
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record DrawOutcome(DrawResult result, RoundSnapshot nextRound) {
    }
}
