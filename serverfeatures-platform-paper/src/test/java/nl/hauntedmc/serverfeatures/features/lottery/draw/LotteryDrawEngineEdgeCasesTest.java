package nl.hauntedmc.serverfeatures.features.lottery.draw;

import nl.hauntedmc.serverfeatures.features.lottery.config.LotterySettings;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.Entry;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundSnapshot;
import nl.hauntedmc.serverfeatures.features.lottery.model.LotteryModels.RoundStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotteryDrawEngineEdgeCasesTest {

    private static final String SEED = "7c".repeat(32);

    @Test
    void emptyRoundCarriesTheWholePotAndStillProducesProof() {
        LotteryDrawEngine engine = new LotteryDrawEngine();
        RoundSnapshot round = round(engine, Money.parse("25.00"), 0, 0);

        var result = engine.draw(round, List.of(), settings(List.of(new BigDecimal("100")), false));

        assertTrue(result.winners().isEmpty());
        assertEquals(Money.ZERO, result.payoutTotal());
        assertEquals(Money.parse("25.00"), result.retainedTotal());
        assertEquals(Money.parse("25.00"), result.nextCarry());
        assertEquals(round.seedCommitment(), result.seedCommitment());
        assertEquals(SEED, result.seedReveal());
        assertEquals(engine.entryDigest(List.of()), result.entryDigest());
    }

    @Test
    void unavailablePrizePositionsAreReassignedWithoutLosingMoney() {
        LotteryDrawEngine engine = new LotteryDrawEngine();
        Entry onlyPlayer = entry("00000000-0000-0000-0000-000000000001", "One", 3);
        RoundSnapshot round = round(engine, Money.parse("13.00"), 3, 1);

        var result = engine.draw(
                round,
                List.of(onlyPlayer),
                settings(List.of(new BigDecimal("70"), new BigDecimal("30")), false)
        );

        assertEquals(1, result.winners().size());
        assertEquals(Money.parse("13.00"), result.winners().getFirst().amount());
        assertEquals(result.grossPot(), result.payoutTotal().add(result.retainedTotal()));
    }

    @Test
    void centRoundingDoesNotCreateZeroValuePayoutRows() {
        LotteryDrawEngine engine = new LotteryDrawEngine();
        List<Entry> entries = List.of(
                entry("00000000-0000-0000-0000-000000000001", "One", 1),
                entry("00000000-0000-0000-0000-000000000002", "Two", 1)
        );
        RoundSnapshot round = round(engine, Money.parse("0.01"), 2, 2);

        var result = engine.draw(
                round,
                entries,
                settings(List.of(new BigDecimal("50"), new BigDecimal("50")), false)
        );

        assertEquals(1, result.winners().size());
        assertEquals(Money.parse("0.01"), result.winners().getFirst().amount());
        assertEquals(Money.parse("0.01"), result.payoutTotal());
        assertEquals(Money.ZERO, result.retainedTotal());
    }

    private static Entry entry(String uuid, String name, int tickets) {
        return new Entry(
                UUID.fromString(uuid),
                1L,
                name,
                tickets,
                Money.parse(Integer.toString(tickets))
        );
    }

    private static RoundSnapshot round(
            LotteryDrawEngine engine,
            Money pot,
            int tickets,
            int participants
    ) {
        return new RoundSnapshot(
                "survival",
                "round",
                RoundStatus.DRAWING,
                0L,
                1L,
                Money.parse("1.00"),
                pot,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                tickets,
                participants,
                0,
                0L,
                engine.commitment(SEED),
                SEED,
                false
        );
    }

    private static LotterySettings settings(List<BigDecimal> shares, boolean repeatedWinners) {
        return new LotterySettings(
                "survival",
                new LotterySettings.Schedule(
                        LotterySettings.ScheduleMode.INTERVAL,
                        Duration.ofHours(12),
                        ZoneId.of("UTC"),
                        List.of()
                ),
                new LotterySettings.Tickets(Money.parse("1.00"), 10, 0, 10),
                new LotterySettings.Pot(Money.ZERO, new BigDecimal("100"), true, Money.parse("1.00")),
                new LotterySettings.Prizes(shares, repeatedWinners),
                new LotterySettings.AntiSnipe(false, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                new LotterySettings.Broadcasts(false, List.of()),
                new LotterySettings.Payouts(true, true),
                new LotterySettings.History(10, 10)
        );
    }
}
