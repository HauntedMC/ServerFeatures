package nl.hauntedmc.serverfeatures.features.lottery.persistence;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryEntryEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPayoutEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryPlayerStatsEntity;
import nl.hauntedmc.serverfeatures.features.lottery.entity.LotteryRoundEntity;
import nl.hauntedmc.serverfeatures.features.lottery.draw.LotteryDrawEngine;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.DonationReceipt;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.DrawResult;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.Entry;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.HistoryItem;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.LeaderboardEntry;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PendingPayout;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PayoutKind;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PayoutStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PlayerSummary;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.PurchaseReceipt;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.Winner;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Small ORM repository built directly around normal Lottery entities. */
public final class LotteryRepository {

    private static final long STALE_DRAW_MILLIS = 300_000L;
    private static final long STALE_PAYOUT_MILLIS = 300_000L;

    private final ORMContext orm;

    public LotteryRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
    }

    public RoundSnapshot ensureOpenRound(
        LotterySettings settings,
        String seed,
        String commitment,
        long now
) {
    try {
        return orm.runInTransaction(session -> {
            failStalePayouts(session, settings.lotteryKey(), now);
            LotteryRoundEntity current = findCurrentRound(
                    session,
                    settings.lotteryKey(),
                    true
            ).orElse(null);
            if (current != null && RoundStatus.DRAWING.name().equals(current.getStatus())
                    && current.getUpdatedAt() < now - STALE_DRAW_MILLIS) {
                current.setStatus(RoundStatus.OPEN.name());
                current.setUpdatedAt(now);
            }
            if (current != null) {
                return snapshot(current);
            }
            LotteryRoundEntity created = newRound(settings, Money.ZERO, seed, commitment, now);
            session.persist(created);
            return snapshot(created);
        });
    } catch (RuntimeException failure) {
        if (!activeRoundConstraintFailure(failure)) {
            throw failure;
        }
        return currentRound(settings.lotteryKey()).orElseThrow(() -> failure);
    }
}

    public Optional<RoundSnapshot> currentRound(String lotteryKey) {
        return orm.runInTransaction(session -> findCurrentRound(session, lotteryKey, false).map(this::snapshot));
    }

    public PurchaseReceipt purchase(
            LotterySettings settings,
            String expectedRoundId,
            UUID playerUuid,
            Long playerId,
            String playerName,
            int ticketCount,
            Money charged,
            long now
    ) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = requiredRound(session, expectedRoundId, settings.lotteryKey(), true);
            requireOpen(round, now);
            String entryId = entryId(round.getId(), playerUuid);
            LotteryEntryEntity entry = session.find(LotteryEntryEntity.class, entryId, LockModeType.PESSIMISTIC_WRITE);
            int existingTickets = entry == null ? 0 : entry.getTicketCount();
            if (settings.tickets().maximumPerPlayer() > 0
                    && existingTickets + ticketCount > settings.tickets().maximumPerPlayer()) {
                throw new LotteryStateException("player ticket limit reached");
            }
            if (settings.tickets().maximumPerRound() > 0
                    && round.getTotalTickets() + ticketCount > settings.tickets().maximumPerRound()) {
                throw new LotteryStateException("round ticket limit reached");
            }
            if (entry == null) {
                entry = new LotteryEntryEntity();
                entry.setId(entryId);
                entry.setLotteryKey(settings.lotteryKey());
                entry.setRoundId(round.getId());
                entry.setPlayerUuid(playerUuid.toString());
                entry.setTicketCount(0);
                entry.setPaidAmount(BigDecimal.ZERO.setScale(Money.SCALE));
                session.persist(entry);
                round.setParticipants(Math.addExact(round.getParticipants(), 1));
            }
            entry.setPlayerId(playerId);
            entry.setPlayerName(trim(playerName, 32));
            entry.setTicketCount(Math.addExact(entry.getTicketCount(), ticketCount));
            entry.setPaidAmount(entry.getPaidAmount().add(charged.amount()));
            entry.setUpdatedAt(now);

            round.setTotalTickets(Math.addExact(round.getTotalTickets(), ticketCount));
            round.setTicketRevenue(round.getTicketRevenue().add(charged.amount()));
            long extension = extension(settings, round, now);
            if (extension > 0L) {
                round.setClosesAt(Math.addExact(round.getClosesAt(), extension));
                round.setExtensionCount(Math.addExact(round.getExtensionCount(), 1));
                round.setTotalExtensionMillis(Math.addExact(round.getTotalExtensionMillis(), extension));
            }
            round.setUpdatedAt(now);

            LotteryPlayerStatsEntity stats = stats(session, settings.lotteryKey(), playerUuid, true);
            updateIdentity(stats, playerId, playerUuid, playerName);
            stats.setTotalSpent(stats.getTotalSpent().add(charged.amount()));
            stats.setTicketsBought(Math.addExact(stats.getTicketsBought(), ticketCount));
            stats.setUpdatedAt(now);

            return new PurchaseReceipt(
                    ticketCount,
                    entry.getTicketCount(),
                    round.getTotalTickets(),
                    round.getParticipants(),
                    charged,
                    snapshot(round).grossPot(),
                    round.getClosesAt(),
                    extension
            );
        });
    }

    public DonationReceipt donate(
            LotterySettings settings,
            String expectedRoundId,
            UUID playerUuid,
            Long playerId,
            String playerName,
            Money amount,
            long now
    ) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = requiredRound(session, expectedRoundId, settings.lotteryKey(), true);
            requireOpen(round, now);
            if (!settings.pot().donationsEnabled()
                    || amount.compareTo(settings.pot().minimumDonation()) < 0) {
                throw new LotteryStateException("donations are disabled or below the minimum");
            }
            round.setDonations(round.getDonations().add(amount.amount()));
            round.setUpdatedAt(now);
            LotteryPlayerStatsEntity stats = stats(session, settings.lotteryKey(), playerUuid, true);
            updateIdentity(stats, playerId, playerUuid, playerName);
            stats.setTotalDonated(stats.getTotalDonated().add(amount.amount()));
            stats.setDonationCount(Math.addExact(stats.getDonationCount(), 1L));
            stats.setUpdatedAt(now);
            return new DonationReceipt(amount, snapshot(round).grossPot());
        });
    }

    public PreparedDraw prepareDraw(String lotteryKey, boolean force, long now) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = findCurrentRound(session, lotteryKey, true)
                    .orElseThrow(() -> new LotteryStateException("no active round"));
            if (!RoundStatus.OPEN.name().equals(round.getStatus())) {
                throw new LotteryStateException("round is already being drawn");
            }
            if (round.isPaused() && !force) {
                throw new LotteryStateException("round is paused");
            }
            if (!force && round.getClosesAt() > now) {
                throw new LotteryStateException("round is not due yet");
            }
            round.setStatus(RoundStatus.DRAWING.name());
            round.setUpdatedAt(now);
            List<LotteryEntryEntity> entities = session.createSelectionQuery(
                            "from LotteryEntryEntity entry where entry.lotteryKey = :key "
                                    + "and entry.roundId = :round order by entry.playerUuid",
                            LotteryEntryEntity.class
                    )
                    .setParameter("key", lotteryKey)
                    .setParameter("round", round.getId())
                    .getResultList();
            List<Entry> entries = entities.stream().map(this::entry).toList();
            return new PreparedDraw(snapshot(round), entries);
        });
    }

    public RoundSnapshot completeDraw(
            LotterySettings settings,
            PreparedDraw prepared,
            DrawResult result,
            String nextSeed,
            String nextCommitment,
            long now
    ) {
        DrawResult expected = new LotteryDrawEngine().draw(
            prepared.round(),
            prepared.entries(),
            settings
    );
    if (!expected.equals(result)) {
        throw new LotteryStateException(
                "draw result does not match the configured deterministic draw"
        );
    }
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = requiredRound(
                    session,
                    prepared.round().roundId(),
                    settings.lotteryKey(),
                    true
            );
            if (!RoundStatus.DRAWING.name().equals(round.getStatus())) {
                throw new LotteryStateException("round is not in drawing state");
            }
            if (!result.roundId().equals(round.getId())) {
                throw new LotteryStateException("draw result belongs to another round");
            }
            Money winnerTotal = result.winners().stream().map(Winner::amount).reduce(Money.ZERO, Money::add);
            if (!winnerTotal.equals(result.payoutTotal())) {
                throw new LotteryStateException("winner amounts do not match the payout total");
            }

            round.setStatus(RoundStatus.COMPLETED.name());
            round.setActiveKey(null);
            round.setDrawnAt(now);
            round.setPayoutTotal(result.payoutTotal().amount());
            round.setRetainedTotal(result.retainedTotal().amount());
            round.setSeedReveal(result.seedReveal());
            round.setEntryDigest(result.entryDigest());
            round.setUpdatedAt(now);

            Set<UUID> roundWinners = new HashSet<>();
            for (Winner winner : result.winners()) {
                LotteryPayoutEntity payout = new LotteryPayoutEntity();
                payout.setId(UUID.randomUUID().toString());
                payout.setLotteryKey(settings.lotteryKey());
                payout.setRoundId(round.getId());
                payout.setPlayerId(winner.playerId());
                payout.setPlayerUuid(winner.playerUuid().toString());
                payout.setPlayerName(trim(winner.playerName(), 32));
                payout.setPosition(winner.position());
                payout.setKind(PayoutKind.WIN.name());
                payout.setStatus(PayoutStatus.PENDING.name());
                payout.setAmount(winner.amount().amount());
                payout.setCreatedAt(now);
                payout.setUpdatedAt(now);
                payout.setPaidAt(0L);
                session.persist(payout);

                LotteryPlayerStatsEntity stats = stats(
                        session,
                        settings.lotteryKey(),
                        winner.playerUuid(),
                        true
                );
                updateIdentity(stats, winner.playerId(), winner.playerUuid(), winner.playerName());
                stats.setTotalWon(stats.getTotalWon().add(winner.amount().amount()));
                if (roundWinners.add(winner.playerUuid())) {
                    stats.setRoundsWon(Math.addExact(stats.getRoundsWon(), 1L));
                }
                stats.setUpdatedAt(now);
            }

            LotteryRoundEntity next = newRound(
                    settings,
                    result.nextCarry(),
                    nextSeed,
                    nextCommitment,
                    now
            );
            session.persist(next);
            return snapshot(next);
        });
    }

    public void releaseDraw(String lotteryKey, String roundId, long now) {
        orm.runInTransaction(session -> {
            LotteryRoundEntity round = session.find(LotteryRoundEntity.class, roundId, LockModeType.PESSIMISTIC_WRITE);
            if (round != null
                    && lotteryKey.equals(round.getLotteryKey())
                    && RoundStatus.DRAWING.name().equals(round.getStatus())) {
                round.setStatus(RoundStatus.OPEN.name());
                round.setUpdatedAt(now);
            }
            return null;
        });
    }

    public RoundSnapshot setPaused(String lotteryKey, boolean paused, long now) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = findCurrentRound(session, lotteryKey, true)
                    .orElseThrow(() -> new LotteryStateException("no active round"));
            round.setPaused(paused);
            round.setUpdatedAt(now);
            return snapshot(round);
        });
    }

    public RoundSnapshot addPot(String lotteryKey, Money amount, long now) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = findCurrentRound(session, lotteryKey, true)
                    .orElseThrow(() -> new LotteryStateException("no active round"));
            if (!RoundStatus.OPEN.name().equals(round.getStatus())) {
                throw new LotteryStateException("round is not open");
            }
            round.setAdminAdditions(round.getAdminAdditions().add(amount.amount()));
            round.setUpdatedAt(now);
            return snapshot(round);
        });
    }

    public CancelledRound cancelRound(
            LotterySettings settings,
            String nextSeed,
            String nextCommitment,
            long now
    ) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = findCurrentRound(session, settings.lotteryKey(), true)
                    .orElseThrow(() -> new LotteryStateException("no active round"));
            List<LotteryEntryEntity> entries = session.createSelectionQuery(
                            "from LotteryEntryEntity entry where entry.lotteryKey = :key and entry.roundId = :round",
                            LotteryEntryEntity.class
                    )
                    .setParameter("key", settings.lotteryKey())
                    .setParameter("round", round.getId())
                    .getResultList();
            Money refunds = Money.ZERO;
            for (LotteryEntryEntity entry : entries) {
                Money amount = Money.of(entry.getPaidAmount());
                if (!amount.isPositive()) {
                    continue;
                }
                refunds = refunds.add(amount);
                LotteryPayoutEntity payout = new LotteryPayoutEntity();
                payout.setId(UUID.randomUUID().toString());
                payout.setLotteryKey(settings.lotteryKey());
                payout.setRoundId(round.getId());
                payout.setPlayerId(entry.getPlayerId());
                payout.setPlayerUuid(entry.getPlayerUuid());
                payout.setPlayerName(entry.getPlayerName());
                payout.setPosition(0);
                payout.setKind(PayoutKind.REFUND.name());
                payout.setStatus(PayoutStatus.PENDING.name());
                payout.setAmount(entry.getPaidAmount());
                payout.setCreatedAt(now);
                payout.setUpdatedAt(now);
                payout.setPaidAt(0L);
                session.persist(payout);

                UUID playerUuid = UUID.fromString(entry.getPlayerUuid());
                LotteryPlayerStatsEntity stats = stats(session, settings.lotteryKey(), playerUuid, true);
                stats.setTotalSpent(stats.getTotalSpent().subtract(entry.getPaidAmount()).max(BigDecimal.ZERO));
                stats.setTicketsBought(Math.max(0L, stats.getTicketsBought() - entry.getTicketCount()));
                stats.setUpdatedAt(now);
            }
            RoundSnapshot before = snapshot(round);
            Money carry = before.grossPot().subtract(refunds);
            round.setStatus(RoundStatus.CANCELLED.name());
            round.setActiveKey(null);
            round.setDrawnAt(now);
            round.setPayoutTotal(refunds.amount());
            round.setRetainedTotal(carry.amount());
            round.setUpdatedAt(now);
            session.flush();
            LotteryRoundEntity next = newRound(settings, carry, nextSeed, nextCommitment, now);
            session.persist(next);
            return new CancelledRound(snapshot(next), refunds);
        });
    }

    public Optional<PendingPayout> reserveNextPayout(String lotteryKey, UUID playerUuid, long now) {
        return orm.runInTransaction(session -> {
            LotteryPayoutEntity payout = session.createSelectionQuery(
                            "from LotteryPayoutEntity payout where payout.lotteryKey = :key "
                                    + "and payout.playerUuid = :uuid and payout.status = :status "
                                    + "order by payout.createdAt",
                            LotteryPayoutEntity.class
                    )
                    .setParameter("key", lotteryKey)
                    .setParameter("uuid", playerUuid.toString())
                    .setParameter("status", PayoutStatus.PENDING.name())
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (payout == null) {
                return Optional.empty();
            }
            payout.setStatus(PayoutStatus.PAYING.name());
            payout.setUpdatedAt(now);
            return Optional.of(payout(payout));
        });
    }

    public void finishPayout(String lotteryKey, String payoutId, PayoutStatus status, String error, long now) {
        orm.runInTransaction(session -> {
            LotteryPayoutEntity payout = session.find(
                    LotteryPayoutEntity.class,
                    payoutId,
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (payout == null || !lotteryKey.equals(payout.getLotteryKey())) {
                return null;
            }
            payout.setStatus(status.name());
            payout.setErrorMessage(error == null ? null : trim(error, 500));
            payout.setUpdatedAt(now);
            if (status == PayoutStatus.PAID) {
                payout.setPaidAt(now);
            }
            return null;
        });
    }

    public PlayerSummary playerSummary(String lotteryKey, UUID playerUuid) {
        return orm.runInTransaction(session -> {
            LotteryRoundEntity round = findCurrentRound(session, lotteryKey, false).orElse(null);
            int tickets = 0;
            int totalTickets = 0;
            if (round != null) {
                LotteryEntryEntity entry = session.find(LotteryEntryEntity.class, entryId(round.getId(), playerUuid));
                tickets = entry == null ? 0 : entry.getTicketCount();
                totalTickets = round.getTotalTickets();
            }
            BigDecimal pending = session.createSelectionQuery(
                            "select coalesce(sum(payout.amount), 0) from LotteryPayoutEntity payout "
                                    + "where payout.lotteryKey = :key and payout.playerUuid = :uuid "
                                    + "and payout.status = :status",
                            BigDecimal.class
                    )
                    .setParameter("key", lotteryKey)
                    .setParameter("uuid", playerUuid.toString())
                    .setParameter("status", PayoutStatus.PENDING.name())
                    .getSingleResult();
            LotteryPlayerStatsEntity stats = stats(session, lotteryKey, playerUuid, false);
            BigDecimal odds = totalTickets == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(tickets)
                    .multiply(new BigDecimal("100"))
                    .divide(BigDecimal.valueOf(totalTickets), 4, RoundingMode.HALF_UP);
            return new PlayerSummary(
                    playerUuid,
                    tickets,
                    odds,
                    Money.of(pending),
                    stats == null ? Money.ZERO : Money.of(stats.getTotalWon()),
                    stats == null ? Money.ZERO : Money.of(stats.getTotalDonated())
            );
        });
    }

    public List<HistoryItem> history(String lotteryKey, int offset, int limit) {
        return orm.runInTransaction(session -> {
            List<LotteryRoundEntity> rounds = session.createSelectionQuery(
                            "from LotteryRoundEntity round where round.lotteryKey = :key "
                                    + "and round.status = :status order by round.drawnAt desc",
                            LotteryRoundEntity.class
                    )
                    .setParameter("key", lotteryKey)
                    .setParameter("status", RoundStatus.COMPLETED.name())
                    .setFirstResult(offset)
                    .setMaxResults(limit)
                    .getResultList();
            List<HistoryItem> result = new ArrayList<>(rounds.size());
            for (LotteryRoundEntity round : rounds) {
                List<Winner> winners = session.createSelectionQuery(
                                "from LotteryPayoutEntity payout where payout.lotteryKey = :key "
                                        + "and payout.roundId = :round and payout.kind = :kind "
                                        + "order by payout.position",
                                LotteryPayoutEntity.class
                        )
                        .setParameter("key", lotteryKey)
                        .setParameter("round", round.getId())
                        .setParameter("kind", PayoutKind.WIN.name())
                        .getResultList()
                        .stream()
                        .map(this::winner)
                        .toList();
                RoundSnapshot snapshot = snapshot(round);
                result.add(new HistoryItem(
                        round.getId(),
                        round.getDrawnAt(),
                        snapshot.grossPot(),
                        Money.of(round.getPayoutTotal()),
                        round.getTotalTickets(),
                        round.getParticipants(),
                        winners,
                        round.getSeedCommitment(),
                        round.getSeedReveal(),
                        round.getEntryDigest() == null ? "" : round.getEntryDigest()
                ));
            }
            return List.copyOf(result);
        });
    }

    public List<LeaderboardEntry> leaderboard(String lotteryKey, boolean donations, int offset, int limit) {
        return orm.runInTransaction(session -> {
            String field = donations ? "stats.totalDonated" : "stats.totalWon";
            List<LotteryPlayerStatsEntity> rows = session.createSelectionQuery(
                            "from LotteryPlayerStatsEntity stats where stats.lotteryKey = :key "
                                    + "and " + field + " > 0 order by " + field + " desc, stats.playerUuid",
                            LotteryPlayerStatsEntity.class
                    )
                    .setParameter("key", lotteryKey)
                    .setFirstResult(offset)
                    .setMaxResults(limit)
                    .getResultList();
            List<LeaderboardEntry> result = new ArrayList<>(rows.size());
            int rank = offset + 1;
            for (LotteryPlayerStatsEntity row : rows) {
                result.add(new LeaderboardEntry(
                        rank++,
                        UUID.fromString(row.getPlayerUuid()),
                        row.getPlayerName(),
                        Money.of(donations ? row.getTotalDonated() : row.getTotalWon()),
                        donations ? row.getDonationCount() : row.getRoundsWon()
                ));
            }
            return List.copyOf(result);
        });
    }

    private Optional<LotteryRoundEntity> findCurrentRound(
        Session session,
        String lotteryKey,
        boolean lock
) {
    var query = session.createSelectionQuery(
                    "from LotteryRoundEntity round where round.activeKey = :key",
                    LotteryRoundEntity.class
            )
            .setParameter("key", lotteryKey)
            .setMaxResults(1);
    if (lock) {
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }
    return query.getResultStream().findFirst();
}

    private LotteryRoundEntity requiredRound(Session session, String id, String lotteryKey, boolean lock) {
        LotteryRoundEntity round = lock
                ? session.find(LotteryRoundEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                : session.find(LotteryRoundEntity.class, id);
        if (round == null || !lotteryKey.equals(round.getLotteryKey())) {
            throw new LotteryStateException("Lottery round is no longer available");
        }
        return round;
    }

    private static void requireOpen(LotteryRoundEntity round, long now) {
        if (!RoundStatus.OPEN.name().equals(round.getStatus()) || round.isPaused() || round.getClosesAt() <= now) {
            throw new LotteryStateException("Lottery round is closed");
        }
    }

    private LotteryRoundEntity newRound(
            LotterySettings settings,
            Money carriedPot,
            String seed,
            String commitment,
            long now
    ) {
        LotteryRoundEntity round = new LotteryRoundEntity();
        round.setId(UUID.randomUUID().toString());
        round.setLotteryKey(settings.lotteryKey());
        round.setStatus(RoundStatus.OPEN.name());
        round.setActiveKey(settings.lotteryKey());
        round.setOpenedAt(now);
        round.setClosesAt(settings.nextCloseAt(now));
        round.setDrawnAt(0L);
        round.setUpdatedAt(now);
        round.setTicketPrice(settings.tickets().price().amount());
        round.setBasePot(settings.pot().baseAmount().amount());
        round.setCarriedPot(carriedPot.amount());
        round.setTicketRevenue(BigDecimal.ZERO.setScale(Money.SCALE));
        round.setDonations(BigDecimal.ZERO.setScale(Money.SCALE));
        round.setAdminAdditions(BigDecimal.ZERO.setScale(Money.SCALE));
        round.setPayoutTotal(BigDecimal.ZERO.setScale(Money.SCALE));
        round.setRetainedTotal(BigDecimal.ZERO.setScale(Money.SCALE));
        round.setTotalTickets(0);
        round.setParticipants(0);
        round.setExtensionCount(0);
        round.setTotalExtensionMillis(0L);
        round.setSeedCommitment(commitment);
        round.setSeedReveal(seed);
        round.setPaused(false);
        return round;
    }

    private long extension(LotterySettings settings, LotteryRoundEntity round, long now) {
        LotterySettings.AntiSnipe antiSnipe = settings.antiSnipe();
        if (!antiSnipe.enabled()
                || round.getClosesAt() - now > antiSnipe.triggerRemaining().toMillis()
                || antiSnipe.extension().isZero()) {
            return 0L;
        }
        long remainingCapacity = antiSnipe.maximumTotalExtension().toMillis() - round.getTotalExtensionMillis();
        return Math.max(0L, Math.min(antiSnipe.extension().toMillis(), remainingCapacity));
    }

    private LotteryPlayerStatsEntity stats(Session session, String lotteryKey, UUID playerUuid, boolean create) {
        String id = statsId(lotteryKey, playerUuid);
        LotteryPlayerStatsEntity stats = session.find(LotteryPlayerStatsEntity.class, id);
        if (stats == null && create) {
            stats = new LotteryPlayerStatsEntity();
            stats.setId(id);
            stats.setLotteryKey(lotteryKey);
            stats.setPlayerUuid(playerUuid.toString());
            stats.setPlayerName(playerUuid.toString());
            stats.setTotalSpent(BigDecimal.ZERO.setScale(Money.SCALE));
            stats.setTotalDonated(BigDecimal.ZERO.setScale(Money.SCALE));
            stats.setTotalWon(BigDecimal.ZERO.setScale(Money.SCALE));
            stats.setTicketsBought(0L);
            stats.setDonationCount(0L);
            stats.setRoundsWon(0L);
            stats.setUpdatedAt(0L);
            session.persist(stats);
        }
        return stats;
    }

    private static void updateIdentity(
            LotteryPlayerStatsEntity stats,
            Long playerId,
            UUID playerUuid,
            String playerName
    ) {
        stats.setPlayerId(playerId);
        stats.setPlayerUuid(playerUuid.toString());
        stats.setPlayerName(trim(playerName, 32));
    }

    private void failStalePayouts(Session session, String lotteryKey, long now) {
        session.createMutationQuery(
                        "update LotteryPayoutEntity payout set payout.status = :failed, "
                                + "payout.errorMessage = :reason, payout.updatedAt = :now "
                                + "where payout.lotteryKey = :key and payout.status = :paying "
                                + "and payout.updatedAt < :cutoff"
                )
                .setParameter("failed", PayoutStatus.FAILED.name())
                .setParameter("reason", "Server stopped while the payout was being delivered")
                .setParameter("now", now)
                .setParameter("key", lotteryKey)
                .setParameter("paying", PayoutStatus.PAYING.name())
                .setParameter("cutoff", now - STALE_PAYOUT_MILLIS)
                .executeUpdate();
    }

    private RoundSnapshot snapshot(LotteryRoundEntity round) {
        return new RoundSnapshot(
                round.getLotteryKey(),
                round.getId(),
                RoundStatus.valueOf(round.getStatus()),
                round.getOpenedAt(),
                round.getClosesAt(),
                Money.of(round.getTicketPrice()),
                Money.of(round.getBasePot()),
                Money.of(round.getCarriedPot()),
                Money.of(round.getTicketRevenue()),
                Money.of(round.getDonations()),
                Money.of(round.getAdminAdditions()),
                Money.of(round.getPayoutTotal()),
                Money.of(round.getRetainedTotal()),
                round.getTotalTickets(),
                round.getParticipants(),
                round.getExtensionCount(),
                round.getTotalExtensionMillis(),
                round.getSeedCommitment(),
                round.getSeedReveal(),
                round.isPaused()
        );
    }

    private Entry entry(LotteryEntryEntity entry) {
        return new Entry(
                UUID.fromString(entry.getPlayerUuid()),
                entry.getPlayerId(),
                entry.getPlayerName(),
                entry.getTicketCount(),
                Money.of(entry.getPaidAmount())
        );
    }

    private PendingPayout payout(LotteryPayoutEntity payout) {
        return new PendingPayout(
                payout.getId(),
                UUID.fromString(payout.getPlayerUuid()),
                Money.of(payout.getAmount()),
                PayoutKind.valueOf(payout.getKind())
        );
    }

    private Winner winner(LotteryPayoutEntity payout) {
        return new Winner(
                payout.getPosition(),
                UUID.fromString(payout.getPlayerUuid()),
                payout.getPlayerId(),
                payout.getPlayerName(),
                0,
                -1L,
                Money.of(payout.getAmount())
        );
    }

    private static boolean activeRoundConstraintFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("uq_lottery_active_round")
                    || message.contains("active_key"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String entryId(String roundId, UUID playerUuid) {
        return roundId + ':' + playerUuid;
    }

    private static String statsId(String lotteryKey, UUID playerUuid) {
        return lotteryKey + ':' + playerUuid;
    }

    private static String trim(String value, int maximumLength) {
        String normalized = value == null || value.isBlank() ? "unknown" : value;
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }

    public record PreparedDraw(RoundSnapshot round, List<Entry> entries) {
        public PreparedDraw {
            entries = List.copyOf(entries);
        }
    }

    public record CancelledRound(RoundSnapshot nextRound, Money refunds) {
    }

    public static final class LotteryStateException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public LotteryStateException(String message) {
            super(message);
        }
    }
}
