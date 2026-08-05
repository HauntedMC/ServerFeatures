package nl.hauntedmc.serverfeatures.features.lottery.draw;

import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.DrawResult;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.Entry;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.Winner;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Deterministic ticket-weighted draw with a public commitment and reveal. */
public final class LotteryDrawEngine {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger TWO_POW_256 = BigInteger.ONE.shiftLeft(256);

    public String newSeed() {
        byte[] seed = new byte[32];
        RANDOM.nextBytes(seed);
        return HexFormat.of().formatHex(seed);
    }

    public String commitment(String seedHex) {
        return hex(digest().digest(HexFormat.of().parseHex(seedHex)));
    }

    public DrawResult draw(RoundSnapshot round, List<Entry> sourceEntries, LotterySettings settings) {
        Objects.requireNonNull(round, "round");
        Objects.requireNonNull(sourceEntries, "sourceEntries");
        Objects.requireNonNull(settings, "settings");

        List<Entry> entries = sourceEntries.stream()
                .filter(entry -> entry.ticketCount() > 0)
                .sorted(Comparator.comparing(entry -> entry.playerUuid().toString()))
                .toList();
        Money gross = round.grossPot();
        String entryDigest = entryDigest(entries);
        if (entries.isEmpty()) {
            return new DrawResult(
                    round.roundId(),
                    gross,
                    Money.ZERO,
                    gross,
                    gross,
                    0,
                    0,
                    List.of(),
                    round.seedCommitment(),
                    round.seedReveal(),
                    entryDigest
            );
        }

        Money payout = gross.percentage(settings.pot().payoutPercentage());
        Money retained = gross.subtract(payout);
        if (payout.isZero()) {
            return new DrawResult(
                    round.roundId(),
                    gross,
                    payout,
                    retained,
                    retained,
                    Math.toIntExact(totalTickets(entries)),
                    entries.size(),
                    List.of(),
                    round.seedCommitment(),
                    round.seedReveal(),
                    entryDigest
            );
        }

        int requestedWinners = settings.prizes().shares().size();
        int winnerCount = settings.prizes().allowSamePlayerMultiplePrizes()
                ? requestedWinners
                : Math.min(requestedWinners, entries.size());
        List<Money> allocations = allocate(payout, settings.prizes().shares(), winnerCount);
        List<Entry> selectable = new ArrayList<>(entries);
        List<Winner> winners = new ArrayList<>();
        for (int index = 0; index < allocations.size(); index++) {
            Money amount = allocations.get(index);
            if (!amount.isPositive()) {
                continue;
            }
            long winningTicket = boundedRandom(
                    round.seedReveal(),
                    round.roundId(),
                    entryDigest,
                    index,
                    totalTickets(selectable)
            );
            Entry winner = ownerOf(selectable, winningTicket);
            winners.add(new Winner(
                    index + 1,
                    winner.playerUuid(),
                    winner.playerId(),
                    winner.playerName(),
                    winner.ticketCount(),
                    winningTicket,
                    amount
            ));
            if (!settings.prizes().allowSamePlayerMultiplePrizes()) {
                selectable.remove(winner);
            }
        }

        Money actualPayout = winners.stream().map(Winner::amount).reduce(Money.ZERO, Money::add);
        Money actualRetained = gross.subtract(actualPayout);
        return new DrawResult(
                round.roundId(),
                gross,
                actualPayout,
                actualRetained,
                actualRetained,
                Math.toIntExact(totalTickets(entries)),
                entries.size(),
                winners,
                round.seedCommitment(),
                round.seedReveal(),
                entryDigest
        );
    }

    public String entryDigest(List<Entry> entries) {
        MessageDigest digest = digest();
        entries.stream()
                .sorted(Comparator.comparing(entry -> entry.playerUuid().toString()))
                .forEach(entry -> {
                    digest.update(entry.playerUuid().toString().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) ':');
                    digest.update(Integer.toString(entry.ticketCount()).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                });
        return hex(digest.digest());
    }

    private static List<Money> allocate(Money payout, List<BigDecimal> shares, int winnerCount) {
        if (winnerCount == 0) {
            return List.of();
        }
        List<Money> result = new ArrayList<>(winnerCount);
        Money allocated = Money.ZERO;
        for (int index = 0; index < winnerCount; index++) {
            Money amount = payout.percentage(shares.get(index));
            result.add(amount);
            allocated = allocated.add(amount);
        }
        result.set(0, result.get(0).add(payout.subtract(allocated)));
        return List.copyOf(result);
    }

    private static long boundedRandom(
            String seedHex,
            String roundId,
            String entryDigest,
            int drawOrdinal,
            long bound
    ) {
        if (bound <= 0L) {
            throw new IllegalArgumentException("bound must be positive");
        }
        BigInteger divisor = BigInteger.valueOf(bound);
        BigInteger rejectionLimit = TWO_POW_256.subtract(TWO_POW_256.mod(divisor));
        byte[] seed = HexFormat.of().parseHex(seedHex);
        for (int counter = 0; counter < Integer.MAX_VALUE; counter++) {
            MessageDigest digest = digest();
            digest.update(seed);
            digest.update(roundId.getBytes(StandardCharsets.UTF_8));
            digest.update(entryDigest.getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(8).putInt(drawOrdinal).putInt(counter).array());
            BigInteger candidate = new BigInteger(1, digest.digest());
            if (candidate.compareTo(rejectionLimit) < 0) {
                return candidate.mod(divisor).longValueExact();
            }
        }
        throw new IllegalStateException("Could not obtain an unbiased Lottery sample");
    }

    private static Entry ownerOf(List<Entry> entries, long winningTicket) {
        long cumulative = 0L;
        for (Entry entry : entries) {
            cumulative = Math.addExact(cumulative, entry.ticketCount());
            if (winningTicket < cumulative) {
                return entry;
            }
        }
        throw new IllegalStateException("Winning ticket is outside the entry range");
    }

    private static long totalTickets(List<Entry> entries) {
        long total = 0L;
        for (Entry entry : entries) {
            total = Math.addExact(total, entry.ticketCount());
        }
        return total;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
