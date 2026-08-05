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

class LotteryDrawEngineTest {

    @Test
    void drawIsDeterministicAndConservesThePot() {
        LotteryDrawEngine engine = new LotteryDrawEngine();
        String seed = "5a".repeat(32);
        RoundSnapshot round = new RoundSnapshot(
                "survival",
                "round",
                RoundStatus.DRAWING,
                0L,
                1L,
                Money.parse("1.00"),
                Money.parse("10.00"),
                Money.ZERO,
                Money.parse("3.00"),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                3,
                2,
                0,
                0L,
                engine.commitment(seed),
                seed,
                false
        );
        List<Entry> entries = List.of(
                new Entry(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1L, "One", 1, Money.parse("1")),
                new Entry(UUID.fromString("00000000-0000-0000-0000-000000000002"), 2L, "Two", 2, Money.parse("2"))
        );
        LotterySettings settings = settings();
        var first = engine.draw(round, entries, settings);
        var second = engine.draw(round, entries, settings);

        assertEquals(first, second);
        assertEquals(first.grossPot(), first.payoutTotal().add(first.retainedTotal()));
        assertEquals(first.payoutTotal(), first.winners().stream()
                .map(winner -> winner.amount())
                .reduce(Money.ZERO, Money::add));
    }

    private static LotterySettings settings() {
        return new LotterySettings(
                "survival",
                new LotterySettings.Schedule(
                        LotterySettings.ScheduleMode.INTERVAL,
                        Duration.ofHours(12),
                        ZoneId.of("UTC"),
                        List.of()
                ),
                new LotterySettings.Tickets(Money.parse("1"), 10, 0, 10),
                new LotterySettings.Pot(Money.ZERO, new BigDecimal("100"), true, Money.parse("1")),
                new LotterySettings.Prizes(List.of(new BigDecimal("70"), new BigDecimal("30")), false),
                new LotterySettings.AntiSnipe(false, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                new LotterySettings.Broadcasts(false, List.of()),
                new LotterySettings.Payouts(true, true),
                new LotterySettings.History(10, 10)
        );
    }
}
