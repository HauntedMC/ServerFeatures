package nl.hauntedmc.serverfeatures.features.lottery.service;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowEvent;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRegistration;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.draw.LotteryDrawEngine;
import nl.hauntedmc.serverfeatures.features.lottery.economy.LotteryEconomyGateway;
import nl.hauntedmc.serverfeatures.features.lottery.economy.LotteryEconomyGateway.EconomyResult;
import nl.hauntedmc.serverfeatures.features.lottery.economy.BuiltinLotteryEconomy;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.DonationReceipt;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PendingPayout;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PayoutStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PlayerSummary;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PurchaseReceipt;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository.PurchaseFulfilment;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/** Coordinates the Lottery gameplay flow while keeping Vault on the main thread. */
public final class LotteryService {

    private final Lottery feature;
    private final LotterySettings settings;
    private final LotteryRepository repository;
    private final LotteryEconomyGateway economy;
    private final LotteryDrawEngine drawEngine;
    private final PlayerIdentityResolver identityResolver;
    private final AtomicReference<RoundSnapshot> snapshot = new AtomicReference<>();
    private final Map<UUID, PlayerSummary> playerSummaries = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> activePlayerOperations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LotteryViewService views;
    private final LotteryDrawCoordinator rounds;
    private EconomyWorkflowRegistration purchaseWorkflowRegistration;
    private final AtomicBoolean reconcilingNativePurchases = new AtomicBoolean();

    public LotteryService(
            Lottery feature,
            LotterySettings settings,
            LotteryRepository repository,
            LotteryEconomyGateway economy,
            LotteryDrawEngine drawEngine
    ) {
        this.feature = feature;
        this.settings = settings;
        this.repository = repository;
        this.economy = economy;
        this.drawEngine = drawEngine;
        this.identityResolver = new PlayerIdentityResolver(feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Lottery")));
        this.views = new LotteryViewService(this, feature, settings, repository);
        this.rounds = new LotteryDrawCoordinator(this, feature, settings, repository, drawEngine);
    }

    public void start() {
        if (economy instanceof BuiltinLotteryEconomy builtin) {
            purchaseWorkflowRegistration = builtin.registerPurchaseHandler(this::fulfilNativePurchase);
            feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(
                    () -> reconcileNativePurchases(builtin), BukkitTime.seconds(5), BukkitTime.seconds(30)
            );
        }
        String seed = drawEngine.newSeed();
        submit(() -> repository.ensureOpenRound(settings, seed, drawEngine.commitment(seed), now()))
                .whenComplete((round, failure) -> main(() -> {
                    if (failure != null) {
                        feature.getLogger().log(Level.SEVERE, "Could not initialize Lottery", unwrap(failure));
                        return;
                    }
                    ready.set(true);
                    updateSnapshot(round);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        refreshPlayerSummary(player.getUniqueId());
                    }
                }));
        rounds.start();
    }

    public void close() {
        closed.set(true);
        ready.set(false);
        snapshot.set(null);
        playerSummaries.clear();
        activePlayerOperations.clear();
        if (purchaseWorkflowRegistration != null) {
            purchaseWorkflowRegistration.close();
            purchaseWorkflowRegistration = null;
        }
        rounds.close();
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

    public String format(Money amount) {
        return economy.format(amount);
    }

    public Money balance(OfflinePlayer player) {
        return economy.cachedBalance(player).orElse(Money.ZERO);
    }

    public int maximumAffordable(Player player) {
        RoundSnapshot round = snapshot.get();
        if (!isReady() || round == null || !round.acceptsEntries(now())) {
            return 0;
        }
        int affordable = economy.cachedBalance(player)
                .map(balance -> balance.amount()
                        .divide(round.ticketPrice().amount(), 0, RoundingMode.DOWN)
                        .min(BigDecimal.valueOf(settings.tickets().maximumPerCommand()))
                        .intValue())
                .orElse(0);
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

    public void explainCannotBuy(Player player) {
        RoundSnapshot round = snapshot.get();
        if (!isReady() || round == null) {
            feature.send(player, "lottery.unavailable");
            return;
        }
        if (!round.acceptsEntries(now())) {
            feature.send(player, round.paused() ? "lottery.paused" : "lottery.closed");
            return;
        }
        PlayerSummary summary = cachedSummary(player.getUniqueId());
        if (settings.tickets().maximumPerPlayer() > 0
                && summary.tickets() >= settings.tickets().maximumPerPlayer()) {
            feature.send(player, "lottery.buy.player_limit", Map.of(
                    "current", Integer.toString(summary.tickets()),
                    "limit", Integer.toString(settings.tickets().maximumPerPlayer())
            ));
            return;
        }
        if (settings.tickets().maximumPerRound() > 0
                && round.totalTickets() >= settings.tickets().maximumPerRound()) {
            feature.send(player, "lottery.buy.round_limit", Map.of("remaining", "0"));
            return;
        }
        Money currentBalance = balance(player);
        feature.send(player, "lottery.buy.insufficient", Map.of(
                "cost", format(round.ticketPrice()),
                "balance", format(currentBalance)
        ));
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
        PlayerSummary summary = cachedSummary(player.getUniqueId());
        if (settings.tickets().maximumPerPlayer() > 0
                && summary.tickets() + ticketCount > settings.tickets().maximumPerPlayer()) {
            feature.send(player, "lottery.buy.player_limit", Map.of(
                    "current", Integer.toString(summary.tickets()),
                    "limit", Integer.toString(settings.tickets().maximumPerPlayer())
            ));
            return;
        }
        if (settings.tickets().maximumPerRound() > 0
                && round.totalTickets() + ticketCount > settings.tickets().maximumPerRound()) {
            feature.send(player, "lottery.buy.round_limit", Map.of(
                    "remaining", Integer.toString(Math.max(
                            0,
                            settings.tickets().maximumPerRound() - round.totalTickets()
                    ))
            ));
            return;
        }
        if (!beginPlayerOperation(player)) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        Money cost = round.ticketPrice().multiply(ticketCount);
        Optional<Money> currentBalance = economy.cachedBalance(player);
        if (currentBalance.isPresent() && currentBalance.get().compareTo(cost) < 0) {
            endPlayerOperation(playerUuid);
            feature.send(player, "lottery.buy.insufficient", Map.of(
                    "cost", format(cost),
                    "balance", format(currentBalance.get())
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
        Optional<Money> currentBalance = economy.cachedBalance(player);
        if (currentBalance.isPresent() && currentBalance.get().compareTo(amount) < 0) {
            feature.send(player, "lottery.donate.insufficient", Map.of(
                    "amount", format(amount),
                    "balance", format(currentBalance.get())
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
        // In-flight work owns this guard and releases it when that work actually ends.
        playerSummaries.remove(player.getUniqueId());
    }

    public void requestOverview(CommandSender sender) {
        views.requestOverview(sender);
    }

    public void requestHistory(CommandSender sender, int page) {
        views.requestHistory(sender, page);
    }

    public void requestLeaderboard(CommandSender sender, boolean donations, int page) {
        views.requestLeaderboard(sender, donations, page);
    }

    public void requestAdminStatus(CommandSender sender) {
        views.requestAdminStatus(sender);
    }

    public void setPaused(CommandSender sender, boolean paused) {
        if (!isReady()) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
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
        if (!isReady()) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
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
        rounds.forceDraw(sender);
    }

    public void cancelRound(CommandSender sender) {
        if (!isReady()) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
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
        return LotteryViewService.formatDuration(millis);
    }

    private void announceTicketPurchase(String playerName, PurchaseReceipt receipt) {
        if (!settings.broadcasts().shouldAnnounceTicketPurchase()
                || playerName == null
                || playerName.isBlank()) {
            return;
        }
        feature.broadcast("lottery.broadcast.ticket_purchase", Map.of(
                "player", playerName,
                "tickets", Integer.toString(receipt.purchasedTickets()),
                "cost", format(receipt.charged()),
                "pot", format(receipt.pot())
        ));
    }

    private void announceDonation(String playerName, DonationReceipt receipt) {
        if (!settings.broadcasts().shouldAnnounceDonation(receipt.amount())
                || playerName == null
                || playerName.isBlank()) {
            return;
        }
        feature.broadcast("lottery.broadcast.donation", Map.of(
                "player", playerName,
                "amount", format(receipt.amount()),
                "pot", format(receipt.pot())
        ));
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
        RoundSnapshot current = snapshot.get();
        if (player == null
                || !player.isOnline()
                || !isReady()
                || current == null
                || !current.roundId().equals(roundId)
                || !current.acceptsEntries(now())) {
            endPlayerOperation(playerUuid);
            if (player != null && player.isOnline()) {
                feature.send(player, current != null && current.paused() ? "lottery.paused" : "lottery.closed");
            }
            return;
        }
        if (economy instanceof BuiltinLotteryEconomy builtin) {
            reserveAndChargeNativePurchase(builtin, playerUuid, playerName, playerId, roundId, ticketCount, cost);
            return;
        }
        String economyKey = "purchase:" + roundId + ":" + playerUuid + ":" + UUID.randomUUID();
        economy.withdraw(player, cost, LotteryEconomyGateway.Operation.PURCHASE, economyKey)
                .whenComplete((withdrawal, economyFailure) -> main(() -> {
            if (economyFailure != null) {
                endPlayerOperation(playerUuid);
                feature.send(player, "lottery.transaction.uncertain");
                log("Lottery purchase withdrawal failed", economyFailure);
                return;
            }
            if (!withdrawal.successful()) {
                endPlayerOperation(playerUuid);
                feature.send(player, withdrawal.uncertain()
                        ? "lottery.transaction.uncertain"
                        : "lottery.buy.withdraw_failed", Map.of("reason", withdrawal.message()));
                return;
            }
            submit(() -> repository.purchase(
                            settings, roundId, playerUuid, playerId, playerName, ticketCount, cost, now()
                    ))
                    .whenComplete((receipt, failure) -> main(() -> {
                        endPlayerOperation(playerUuid);
                        if (failure != null) {
                            refund(player, cost, "purchase", economyKey, failure);
                            return;
                        }
                        rounds.refreshRound();
                        refreshPlayerSummary(playerUuid);
                        feature.send(player, "lottery.buy.success", Map.of(
                                "tickets", Integer.toString(receipt.purchasedTickets()),
                                "cost", format(receipt.charged()),
                                "player_tickets", Integer.toString(receipt.playerTickets()),
                                "pot", format(receipt.pot())
                        ));
                        announceTicketPurchase(playerName, receipt);
                    }));
                }));
    }

    private void reserveAndChargeNativePurchase(
            BuiltinLotteryEconomy builtin,
            UUID playerUuid,
            String playerName,
            Long playerId,
            String roundId,
            int ticketCount,
            Money cost
    ) {
        if (playerId == null || playerId <= 0L) {
            endPlayerOperation(playerUuid);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                feature.send(player, "lottery.identity_unavailable");
            }
            return;
        }
        submit(() -> repository.reservePurchase(
                        settings, roundId, playerUuid, playerId, playerName, ticketCount, cost, now()
                ))
                .whenComplete((intent, reservationFailure) -> main(() -> {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (reservationFailure != null) {
                        endPlayerOperation(playerUuid);
                        if (player != null && player.isOnline()) {
                            feature.send(player, "lottery.buy.withdraw_failed", Map.of(
                                    "reason", rootMessage(reservationFailure)
                            ));
                        }
                        return;
                    }
                    builtin.chargePurchase(playerUuid, playerName, playerId, cost, intent.id())
                            .whenComplete((workflow, chargeFailure) -> main(() -> {
                                if (chargeFailure != null || workflow == null) {
                                    endPlayerOperation(playerUuid);
                                    if (player != null && player.isOnline()) {
                                        feature.send(player, "lottery.transaction.uncertain");
                                    }
                                    if (chargeFailure != null) {
                                        log("Could not start durable Lottery purchase " + intent.id(), chargeFailure);
                                    }
                                    return;
                                }
                                if (workflow.charged()) {
                                    // The registered handler sends the success message only after the entry is durable.
                                    return;
                                }
                                endPlayerOperation(playerUuid);
                                if (workflow.transaction().status() != EconomyResultStatus.TEMPORARY_FAILURE) {
                                    submit(() -> {
                                        repository.declinePurchase(intent.id(), now());
                                        return null;
                                    });
                                }
                                if (player != null && player.isOnline()) {
                                    feature.send(player, workflow.transaction().status() == EconomyResultStatus.TEMPORARY_FAILURE
                                            ? "lottery.transaction.uncertain"
                                            : "lottery.buy.withdraw_failed", Map.of(
                                                    "reason", workflow.transaction().message()
                                            ));
                                }
                            }));
                }));
    }

    /** Completes or compensates one committed native-Economy ticket charge. */
    private CompletableFuture<Void> fulfilNativePurchase(EconomyWorkflowEvent event) {
        if (!(economy instanceof BuiltinLotteryEconomy builtin)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Native Lottery workflow has no native backend"));
        }
        String intentId = event.metadata().get("purchase_intent_id");
        if (intentId == null || !intentId.equals(event.workflow().workflowId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Lottery workflow does not identify its purchase intent"));
        }
        return submit(() -> repository.fulfilPurchase(settings, intentId, Money.of(event.amount()), now()))
                .thenCompose(fulfilment -> fulfilment.refundRequired()
                        ? refundNativePurchase(builtin, event)
                        : notifyNativePurchaseFulfilled(event, fulfilment));
    }

    private CompletableFuture<Void> refundNativePurchase(
            BuiltinLotteryEconomy builtin,
            EconomyWorkflowEvent event
    ) {
        return builtin.refundPurchase(event).thenCompose(refund -> {
            if (!refund.successful()) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Lottery purchase refund was not accepted: " + refund.message()
                ));
            }
            return submit(() -> repository.markPurchaseRefunded(event.workflow().workflowId(), now()));
        }).thenAccept(intent -> main(() -> {
            UUID playerUuid = intent.playerUuid();
            endPlayerOperation(playerUuid);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                feature.send(player, "lottery.transaction.refunded", Map.of("amount", format(intent.charged())));
            }
        })).toCompletableFuture();
    }

    private CompletableFuture<Void> notifyNativePurchaseFulfilled(
            EconomyWorkflowEvent event,
            PurchaseFulfilment fulfilment
    ) {
        main(() -> {
            UUID playerUuid = event.account().playerUuid();
            endPlayerOperation(playerUuid);
            rounds.refreshRound();
            refreshPlayerSummary(playerUuid);
            PurchaseReceipt receipt = fulfilment.receipt();
            if (receipt == null) {
                return;
            }
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                feature.send(player, "lottery.buy.success", Map.of(
                        "tickets", Integer.toString(receipt.purchasedTickets()),
                        "cost", format(receipt.charged()),
                        "player_tickets", Integer.toString(receipt.playerTickets()),
                        "pot", format(receipt.pot())
                ));
            }
            announceTicketPurchase(event.account().playerName(), receipt);
        });
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Reissues only uncertain, still-open reservations using their original workflow id.  A committed
     * debit is discovered first, while a closed round releases an uncharged reservation instead of
     * charging and immediately refunding it.
     */
    private void reconcileNativePurchases(BuiltinLotteryEconomy builtin) {
        if (!isReady() || !reconcilingNativePurchases.compareAndSet(false, true)) {
            return;
        }
        submit(() -> repository.pendingPurchases(settings.lotteryKey(), 32))
                .whenComplete((intents, queryFailure) -> {
                    if (queryFailure != null || intents == null) {
                        if (queryFailure != null) {
                            log("Could not reconcile durable Lottery purchases", queryFailure);
                        }
                        reconcilingNativePurchases.set(false);
                        return;
                    }
                    CompletableFuture<?>[] reconciliations = intents.stream()
                            .map(intent -> reconcileNativePurchase(builtin, intent).toCompletableFuture())
                            .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(reconciliations).whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            log("Could not reconcile a durable Lottery purchase", failure);
                        }
                        reconcilingNativePurchases.set(false);
                    });
                });
    }

    private java.util.concurrent.CompletionStage<Void> reconcileNativePurchase(
            BuiltinLotteryEconomy builtin,
            LotteryRepository.PurchaseIntent intent
    ) {
        return builtin.purchaseWorkflow(intent.id()).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(null);
            }
            RoundSnapshot round = snapshot.get();
            if (round == null || !round.roundId().equals(intent.roundId()) || !round.acceptsEntries(now())) {
                return submit(() -> {
                    repository.declinePurchase(intent.id(), now());
                    return null;
                });
            }
            return builtin.chargePurchase(
                    intent.playerUuid(), intent.playerName(), intent.playerId(), intent.charged(), intent.id()
            ).thenCompose(result -> {
                if (result.charged() || result.transaction().status() == EconomyResultStatus.TEMPORARY_FAILURE) {
                    return CompletableFuture.completedFuture(null);
                }
                return submit(() -> {
                    repository.declinePurchase(intent.id(), now());
                    return null;
                });
            });
        });
    }

    private void withdrawAndStoreDonation(
            UUID playerUuid,
            String playerName,
            Long playerId,
            String roundId,
            Money amount
    ) {
        Player player = Bukkit.getPlayer(playerUuid);
        RoundSnapshot current = snapshot.get();
        if (player == null
                || !player.isOnline()
                || !isReady()
                || current == null
                || !current.roundId().equals(roundId)
                || !current.acceptsEntries(now())) {
            endPlayerOperation(playerUuid);
            if (player != null && player.isOnline()) {
                feature.send(player, current != null && current.paused() ? "lottery.paused" : "lottery.closed");
            }
            return;
        }
        String economyKey = "donation:" + roundId + ":" + playerUuid + ":" + UUID.randomUUID();
        economy.withdraw(player, amount, LotteryEconomyGateway.Operation.DONATION, economyKey)
                .whenComplete((withdrawal, economyFailure) -> main(() -> {
            if (economyFailure != null) {
                endPlayerOperation(playerUuid);
                feature.send(player, "lottery.transaction.uncertain");
                log("Lottery donation withdrawal failed", economyFailure);
                return;
            }
            if (!withdrawal.successful()) {
                endPlayerOperation(playerUuid);
                feature.send(player, withdrawal.uncertain()
                        ? "lottery.transaction.uncertain"
                        : "lottery.donate.withdraw_failed", Map.of("reason", withdrawal.message()));
                return;
            }
            submit(() -> repository.donate(
                            settings, roundId, playerUuid, playerId, playerName, amount, now()
                    ))
                    .whenComplete((receipt, failure) -> main(() -> {
                        endPlayerOperation(playerUuid);
                        if (failure != null) {
                            refund(player, amount, "donation", economyKey, failure);
                            return;
                        }
                        rounds.refreshRound();
                        refreshPlayerSummary(playerUuid);
                        feature.send(player, "lottery.donate.success", Map.of(
                                "amount", format(receipt.amount()),
                                "pot", format(receipt.pot())
                        ));
                        announceDonation(playerName, receipt);
                    }));
                }));
    }

    private void refund(
            Player player,
            Money amount,
            String transaction,
            String originalEconomyKey,
            Throwable failure
    ) {
        economy.deposit(player, amount, LotteryEconomyGateway.Operation.REFUND, "refund:" + originalEconomyKey)
                .whenComplete((refund, refundFailure) -> main(() -> {
            if (refundFailure == null && refund != null && refund.successful()) {
                feature.send(player, "lottery.transaction.refunded", Map.of("amount", format(amount)));
            } else {
                feature.send(player, "lottery.transaction.uncertain");
            }
            String result = refundFailure != null ? rootMessage(refundFailure)
                    : refund == null ? "no result" : refund.message();
            log("Lottery " + transaction + " could not be stored; refund result: " + result, failure);
                }));
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
                    Player currentPlayer = Bukkit.getPlayer(playerUuid);
                    if (currentPlayer == null || !currentPlayer.isOnline() || !isReady()) {
                        releasePayout(optional.get(), playerUuid);
                        return;
                    }
                    deliverPayout(currentPlayer, optional.get(), automatic, paid);
                }));
    }

    private void deliverPayout(Player player, PendingPayout payout, boolean automatic, Money paid) {
        economy.deposit(player, payout.amount(), LotteryEconomyGateway.Operation.PAYOUT, "payout:" + payout.payoutId())
                .whenComplete((result, economyFailure) -> main(() -> {
                    EconomyResult effective = economyFailure == null && result != null
                            ? result
                            : EconomyResult.uncertain(economyFailure == null
                                    ? "No economy result" : rootMessage(economyFailure));
                    PayoutStatus status = effective.successful()
                            ? PayoutStatus.PAID
                            : effective.uncertain() ? PayoutStatus.FAILED : PayoutStatus.PENDING;
                    submit(() -> {
                        boolean updated = repository.finishPayout(
                                settings.lotteryKey(), payout.payoutId(), status,
                                effective.successful() ? null : effective.message(), now()
                        );
                        if (!updated) {
                            throw new IllegalStateException("Payout is no longer reserved for delivery");
                        }
                        return null;
                    }).whenComplete((ignored, failure) -> main(() -> {
                        if (failure != null) {
                            endPlayerOperation(player.getUniqueId());
                            feature.send(player, "lottery.transaction.uncertain");
                            log("Could not save Lottery payout state " + payout.payoutId(), failure);
                            return;
                        }
                        if (!effective.successful()) {
                            endPlayerOperation(player.getUniqueId());
                            if (!automatic) {
                                feature.send(player, effective.uncertain()
                                        ? "lottery.transaction.uncertain"
                                        : "lottery.claim.payout_failed", Map.of("reason", effective.message()));
                            }
                            refreshPlayerSummary(player.getUniqueId());
                            return;
                        }
                        claimNext(player.getUniqueId(), automatic, paid.add(payout.amount()));
                    }));
                }));
    }

    private void releasePayout(PendingPayout payout, UUID playerUuid) {
        submit(() -> {
            boolean released = repository.finishPayout(
                    settings.lotteryKey(),
                    payout.payoutId(),
                    PayoutStatus.PENDING,
                    null,
                    now()
            );
            if (!released) {
                throw new IllegalStateException("Payout is no longer reserved for delivery");
            }
            return null;
        }).whenComplete((ignored, failure) -> {
            endPlayerOperation(playerUuid);
            if (failure != null) {
                log("Could not release Lottery payout " + payout.payoutId(), failure);
            }
        });
    }

    void refreshPlayerSummary(UUID playerUuid) {
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

    void updateSnapshot(RoundSnapshot round) {
        RoundSnapshot previous = snapshot.getAndSet(round);
        rounds.onSnapshotUpdated(previous, round);
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

    void actionFailed(CommandSender sender, Throwable failure) {
        feature.send(sender, "lottery.admin.action_failed", Map.of("reason", rootMessage(failure)));
        log("Lottery administrator action failed", failure);
    }

    <T> CompletableFuture<T> submit(java.util.function.Supplier<T> supplier) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Lottery is closed"));
        }
        return feature.getLifecycleManager().getTaskManager().supplyAsync(supplier);
    }

    void main(Runnable task) {
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

    long now() {
        return System.currentTimeMillis();
    }

    void log(String message, Throwable failure) {
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
}
