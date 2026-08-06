package nl.hauntedmc.serverfeatures.features.lottery.service;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.lottery.Lottery;
import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.draw.LotteryDrawEngine;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.DrawResult;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundStatus;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository;
import nl.hauntedmc.serverfeatures.features.lottery.persistence.LotteryRepository.PreparedDraw;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns round refreshes, announcements and draw execution. */
final class LotteryDrawCoordinator {

    private static final long ROUND_REFRESH_INTERVAL_MILLIS = 30_000L;
    private static final long DRAW_RETRY_DELAY_MILLIS = 5_000L;

    private final LotteryService service;
    private final Lottery feature;
    private final LotterySettings settings;
    private final LotteryRepository repository;
    private final LotteryDrawEngine drawEngine;
    private final AtomicBoolean drawing = new AtomicBoolean();
    private final Set<Long> announcedThresholds = new HashSet<>();
    private long lastRoundRefresh;
    private volatile long nextDrawAttemptAt;

    LotteryDrawCoordinator(
            LotteryService service,
            Lottery feature,
            LotterySettings settings,
            LotteryRepository repository,
            LotteryDrawEngine drawEngine
    ) {
        this.service = service;
        this.feature = feature;
        this.settings = settings;
        this.repository = repository;
        this.drawEngine = drawEngine;
    }

    void start() {
        lastRoundRefresh = service.now();
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::tick,
                BukkitTime.ticks(20L),
                BukkitTime.ticks(20L)
        );
    }

    void close() {
        drawing.set(false);
        announcedThresholds.clear();
    }

    void forceDraw(CommandSender sender) {
        if (!service.isReady()) {
            feature.send(sender, "lottery.unavailable");
            return;
        }
        if (draw(true, sender)) {
            feature.send(sender, "lottery.admin.draw_started");
        }
    }

    void refreshRound() {
        if (!service.isReady()) {
            return;
        }
        String seed = drawEngine.newSeed();
        service.submit(() -> repository.ensureOpenRound(
                        settings,
                        seed,
                        drawEngine.commitment(seed),
                        service.now()
                ))
                .whenComplete((round, failure) -> {
                    if (failure == null && round != null && service.isReady()) {
                        service.main(() -> service.updateSnapshot(round));
                    } else if (failure != null) {
                        service.log("Could not refresh Lottery round", failure);
                    }
                });
    }

    void onSnapshotUpdated(RoundSnapshot previous, RoundSnapshot round) {
        if (previous == null || !previous.roundId().equals(round.roundId())) {
            initializeAnnouncementState(round, service.now());
        }
    }

    private void tick() {
        if (!service.isReady()) {
            return;
        }
        long currentTime = service.now();
        RoundSnapshot round = service.snapshot().orElse(null);
        if (round == null) {
            return;
        }
        if (currentTime - lastRoundRefresh >= ROUND_REFRESH_INTERVAL_MILLIS) {
            lastRoundRefresh = currentTime;
            refreshRound();
        }
        announceRemaining(round, currentTime);
        if (!round.paused()
                && round.status() == RoundStatus.OPEN
                && round.closesAt() <= currentTime
                && currentTime >= nextDrawAttemptAt) {
            draw(false, null);
        }
    }

    private boolean draw(boolean force, CommandSender requester) {
        long currentTime = service.now();
        if (!service.isReady() || (!force && currentTime < nextDrawAttemptAt)) {
            return false;
        }
        if (!drawing.compareAndSet(false, true)) {
            if (requester != null) {
                feature.send(requester, "lottery.admin.action_failed", Map.of(
                        "reason", "a draw is already running"
                ));
            }
            return false;
        }
        nextDrawAttemptAt = Math.addExact(currentTime, DRAW_RETRY_DELAY_MILLIS);
        service.submit(() -> repository.prepareDraw(settings.lotteryKey(), force, service.now()))
                .whenComplete((prepared, prepareFailure) -> service.main(() -> {
                    if (prepareFailure != null) {
                        drawing.set(false);
                        refreshRound();
                        if (requester != null) {
                            service.actionFailed(requester, prepareFailure);
                        } else {
                            service.log("Could not prepare Lottery draw", prepareFailure);
                        }
                        return;
                    }
                    completeDraw(prepared, requester);
                }));
        return true;
    }

    private void completeDraw(PreparedDraw prepared, CommandSender requester) {
        service.submit(() -> {
            DrawResult result = drawEngine.draw(prepared.round(), prepared.entries(), settings);
            String nextSeed = drawEngine.newSeed();
            RoundSnapshot nextRound = repository.completeDraw(
                    settings,
                    prepared,
                    result,
                    nextSeed,
                    drawEngine.commitment(nextSeed),
                    service.now()
            );
            return new DrawOutcome(result, nextRound);
        }).whenComplete((outcome, failure) -> service.main(() -> {
            drawing.set(false);
            if (failure != null) {
                service.submit(() -> {
                    repository.releaseDraw(
                            settings.lotteryKey(),
                            prepared.round().roundId(),
                            service.now()
                    );
                    return null;
                }).exceptionally(releaseFailure -> {
                    service.log("Could not release failed Lottery draw", releaseFailure);
                    return null;
                });
                feature.broadcast("lottery.broadcast.draw_failed", Map.of());
                if (requester != null) {
                    service.actionFailed(requester, failure);
                } else {
                    service.log("Lottery draw failed", failure);
                }
                refreshRound();
                return;
            }
            nextDrawAttemptAt = 0L;
            service.updateSnapshot(outcome.nextRound());
            broadcastDraw(outcome.result());
            for (var winner : outcome.result().winners()) {
                service.refreshPlayerSummary(winner.playerUuid());
                Player online = Bukkit.getPlayer(winner.playerUuid());
                if (online != null && settings.payouts().automaticOnJoin()) {
                    service.claim(online, true);
                }
            }
        }));
    }

    private void broadcastDraw(DrawResult result) {
        if (result.tickets() == 0) {
            feature.broadcast("lottery.broadcast.no_tickets", Map.of(
                    "carry", service.format(result.nextCarry())
            ));
        } else {
            feature.broadcast("lottery.broadcast.draw_header", Map.of(
                    "pot", service.format(result.grossPot()),
                    "tickets", Integer.toString(result.tickets()),
                    "participants", Integer.toString(result.participants())
            ));
            if (result.winners().isEmpty()) {
                feature.broadcast("lottery.broadcast.no_payout", Map.of(
                        "carry", service.format(result.nextCarry())
                ));
            } else {
                for (var winner : result.winners()) {
                    feature.broadcast("lottery.broadcast.winner", Map.of(
                            "position", Integer.toString(winner.position()),
                            "player", winner.playerName(),
                            "amount", service.format(winner.amount())
                    ));
                }
            }
        }
        feature.broadcast("lottery.broadcast.proof", Map.of(
                "round", result.roundId(),
                "commitment", result.seedCommitment(),
                "seed", result.seedReveal(),
                "entry_digest", result.entryDigest()
        ));
    }

    private void announceRemaining(RoundSnapshot round, long currentTime) {
        if (!settings.broadcasts().enabled()) {
            return;
        }
        long remaining = round.remainingMillis(currentTime);
        long selected = Long.MAX_VALUE;
        for (var threshold : settings.broadcasts().remainingTimes()) {
            long millis = threshold.toMillis();
            if (remaining <= millis && !announcedThresholds.contains(millis)) {
                selected = Math.min(selected, millis);
            }
        }
        for (var threshold : settings.broadcasts().remainingTimes()) {
            if (remaining <= threshold.toMillis()) {
                announcedThresholds.add(threshold.toMillis());
            }
        }
        if (round.paused() || selected == Long.MAX_VALUE) {
            return;
        }
        feature.broadcast("lottery.broadcast.remaining", Map.of(
                "remaining", LotteryViewService.formatDuration(remaining),
                "pot", service.format(round.grossPot())
        ));
    }

    private void initializeAnnouncementState(RoundSnapshot round, long currentTime) {
        announcedThresholds.clear();
        long remaining = round.remainingMillis(currentTime);
        for (var threshold : settings.broadcasts().remainingTimes()) {
            if (remaining <= threshold.toMillis()) {
                announcedThresholds.add(threshold.toMillis());
            }
        }
    }

    private record DrawOutcome(DrawResult result, RoundSnapshot nextRound) {
    }
}
