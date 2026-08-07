package nl.hauntedmc.serverfeatures.features.lottery.config;

import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable and strictly validated Lottery configuration. */
public record LotterySettings(
        String lotteryKey,
        Economy economy,
        Schedule schedule,
        Tickets tickets,
        Pot pot,
        Prizes prizes,
        AntiSnipe antiSnipe,
        Broadcasts broadcasts,
        Payouts payouts,
        History history
) {

    public LotterySettings {
        if (lotteryKey == null || !lotteryKey.matches("[a-z0-9][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("lottery_key must match [a-z0-9][a-z0-9_.-]{0,63}");
        }
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(tickets, "tickets");
        Objects.requireNonNull(pot, "pot");
        Objects.requireNonNull(prizes, "prizes");
        Objects.requireNonNull(antiSnipe, "antiSnipe");
        Objects.requireNonNull(broadcasts, "broadcasts");
        Objects.requireNonNull(payouts, "payouts");
        Objects.requireNonNull(history, "history");
    }

    public LotterySettings(
            String lotteryKey,
            Schedule schedule,
            Tickets tickets,
            Pot pot,
            Prizes prizes,
            AntiSnipe antiSnipe,
            Broadcasts broadcasts,
            Payouts payouts,
            History history
    ) {
        this(lotteryKey, new Economy(EconomyBackend.VAULT, "money"), schedule, tickets, pot, prizes,
                antiSnipe, broadcasts, payouts, history);
    }

    public static LotterySettings load(FeatureConfigHandler config, String serverName) {
        String configuredKey = text(config, "lottery_key", "$server");
        String lotteryKey = "$server".equalsIgnoreCase(configuredKey)
                ? normalizeKey(serverName)
                : normalizeKey(configuredKey);

        Economy economy = new Economy(
                enumValue(EconomyBackend.class, text(config, "economy.backend", "VAULT"), "economy.backend"),
                normalizeKey(text(config, "economy.builtin.currency", "money"))
        );

        ScheduleMode scheduleMode = enumValue(
                ScheduleMode.class,
                text(config, "schedule.mode", "INTERVAL"),
                "schedule.mode"
        );
        Duration interval = duration(config, "schedule.interval", "12h", Duration.ofMinutes(1), Duration.ofDays(30));
        ZoneId timezone;
        try {
            timezone = ZoneId.of(text(config, "schedule.timezone", "Europe/Amsterdam"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid schedule.timezone", exception);
        }
        List<LocalTime> fixedTimes = new ArrayList<>();
        for (String value : config.getList("schedule.fixed_times", String.class, List.of())) {
            try {
                fixedTimes.add(LocalTime.parse(value.trim()));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid schedule.fixed_times value: " + value, exception);
            }
        }
        fixedTimes = fixedTimes.stream().distinct().sorted().toList();
        if (scheduleMode == ScheduleMode.FIXED_TIMES && fixedTimes.isEmpty()) {
            throw new IllegalArgumentException("schedule.fixed_times cannot be empty in FIXED_TIMES mode");
        }

        Money ticketPrice = positiveMoney(config, "tickets.price", "500.00");
        int maximumPerPlayer = integer(config, "tickets.maximum_per_player", 1, 0, 1_000_000);
        int maximumPerRound = integer(config, "tickets.maximum_per_round", 0, 0, Integer.MAX_VALUE);
        int maximumPerCommand = integer(config, "tickets.maximum_per_command", 100, 1, 1_000_000);

        Money basePot = nonNegativeMoney(config, "pot.base_amount", "1000.00");
        BigDecimal payoutPercentage = decimal(config, "pot.payout_percentage", "100.00");
        if (payoutPercentage.signum() <= 0 || payoutPercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("pot.payout_percentage must be greater than 0 and at most 100");
        }
        boolean donationsEnabled = config.get("pot.donations_enabled", Boolean.class, true);
        Money minimumDonation = positiveMoney(config, "pot.minimum_donation", "1.00");

        List<BigDecimal> shares = new ArrayList<>();
        for (String rawShare : config.getList("prizes.shares", String.class, List.of("100.00"))) {
            BigDecimal share;
            try {
                share = new BigDecimal(rawShare.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid prizes.shares value: " + rawShare, exception);
            }
            if (share.signum() <= 0) {
                throw new IllegalArgumentException("prizes.shares values must be positive");
            }
            shares.add(share);
        }
        if (shares.isEmpty() || shares.size() > 20) {
            throw new IllegalArgumentException("prizes.shares must contain between 1 and 20 values");
        }
        if (shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("prizes.shares must total exactly 100");
        }

        AntiSnipe antiSnipe = new AntiSnipe(
                config.get("anti_snipe.enabled", Boolean.class, true),
                duration(config, "anti_snipe.trigger_remaining", "30s", Duration.ZERO, Duration.ofHours(1)),
                duration(config, "anti_snipe.extension", "15s", Duration.ZERO, Duration.ofHours(1)),
                duration(config, "anti_snipe.maximum_total_extension", "5m", Duration.ZERO, Duration.ofDays(1))
        );

        List<Duration> remainingTimes = new ArrayList<>();
        for (String value : config.getList(
                "broadcasts.remaining_times",
                String.class,
                List.of("1h", "30m", "10m", "5m", "1m", "30s", "10s")
        )) {
            Duration threshold = parseDuration(value, "broadcasts.remaining_times");
            if (threshold.isZero() || threshold.isNegative()) {
                throw new IllegalArgumentException("broadcasts.remaining_times values must be positive");
            }
            remainingTimes.add(threshold);
        }
        remainingTimes = remainingTimes.stream().distinct().sorted(Comparator.reverseOrder()).toList();

        return new LotterySettings(
                lotteryKey,
                economy,
                new Schedule(scheduleMode, interval, timezone, fixedTimes),
                new Tickets(ticketPrice, maximumPerPlayer, maximumPerRound, maximumPerCommand),
                new Pot(basePot, payoutPercentage, donationsEnabled, minimumDonation),
                new Prizes(shares, config.get("prizes.allow_same_player_multiple_prizes", Boolean.class, false)),
                antiSnipe,
                new Broadcasts(
                        config.get("broadcasts.enabled", Boolean.class, true),
                        remainingTimes,
                        config.get("broadcasts.ticket_purchases.enabled", Boolean.class, false),
                        config.get("broadcasts.donations.enabled", Boolean.class, false),
                        nonNegativeMoney(config, "broadcasts.donations.minimum_amount", "1000.00")
                ),
                new Payouts(
                        config.get("payouts.automatic_on_join", Boolean.class, true),
                        config.get("payouts.claim_command_enabled", Boolean.class, true)
                ),
                new History(
                        integer(config, "history.page_size", 10, 1, 50),
                        integer(config, "history.leaderboard_size", 10, 1, 100)
                )
        );
    }

    public long nextCloseAt(long now) {
        return schedule.nextCloseAt(now);
    }

    public record Economy(EconomyBackend backend, String builtinCurrency) {
        public Economy {
            Objects.requireNonNull(backend, "backend");
            builtinCurrency = normalizeKey(builtinCurrency);
        }
    }

    public enum EconomyBackend {
        BUILTIN,
        VAULT
    }

    public record Schedule(ScheduleMode mode, Duration interval, ZoneId timezone, List<LocalTime> fixedTimes) {
        public Schedule {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(interval, "interval");
            Objects.requireNonNull(timezone, "timezone");
            fixedTimes = List.copyOf(fixedTimes);
        }

        public long nextCloseAt(long now) {
            if (mode == ScheduleMode.INTERVAL) {
                return Math.addExact(now, interval.toMillis());
            }
            ZonedDateTime current = Instant.ofEpochMilli(now).atZone(timezone);
            LocalDate date = current.toLocalDate();
            for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
                LocalDate candidateDate = date.plusDays(dayOffset);
                for (LocalTime time : fixedTimes) {
                    ZonedDateTime candidate = candidateDate.atTime(time).atZone(timezone);
                    if (candidate.toInstant().toEpochMilli() > now) {
                        return candidate.toInstant().toEpochMilli();
                    }
                }
            }
            throw new IllegalStateException("Could not calculate the next fixed Lottery draw");
        }
    }

    public record Tickets(Money price, int maximumPerPlayer, int maximumPerRound, int maximumPerCommand) {
        public Tickets {
            Objects.requireNonNull(price, "price");
        }
    }

    public record Pot(
            Money baseAmount,
            BigDecimal payoutPercentage,
            boolean donationsEnabled,
            Money minimumDonation
    ) {
        public Pot {
            Objects.requireNonNull(baseAmount, "baseAmount");
            Objects.requireNonNull(payoutPercentage, "payoutPercentage");
            Objects.requireNonNull(minimumDonation, "minimumDonation");
        }
    }

    public record Prizes(List<BigDecimal> shares, boolean allowSamePlayerMultiplePrizes) {
        public Prizes {
            shares = List.copyOf(shares);
        }
    }

    public record AntiSnipe(
            boolean enabled,
            Duration triggerRemaining,
            Duration extension,
            Duration maximumTotalExtension
    ) {
    }

    public record Broadcasts(
            boolean enabled,
            List<Duration> remainingTimes,
            boolean ticketPurchasesEnabled,
            boolean donationsEnabled,
            Money donationMinimumAmount
    ) {
        public Broadcasts {
            remainingTimes = List.copyOf(remainingTimes);
            Objects.requireNonNull(donationMinimumAmount, "donationMinimumAmount");
            if (donationMinimumAmount.amount().signum() < 0) {
                throw new IllegalArgumentException("donationMinimumAmount cannot be negative");
            }
        }

        public Broadcasts(boolean enabled, List<Duration> remainingTimes) {
            this(enabled, remainingTimes, false, false, Money.parse("1000.00"));
        }

        public boolean shouldAnnounceTicketPurchase() {
            return ticketPurchasesEnabled;
        }

        public boolean shouldAnnounceDonation(Money amount) {
            Objects.requireNonNull(amount, "amount");
            return donationsEnabled && amount.compareTo(donationMinimumAmount) >= 0;
        }
    }

    public record Payouts(boolean automaticOnJoin, boolean claimCommandEnabled) {
    }

    public record History(int pageSize, int leaderboardSize) {
    }

    public enum ScheduleMode {
        INTERVAL,
        FIXED_TIMES
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Lottery key cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid Lottery key: " + value);
        }
        return normalized;
    }

    private static String text(FeatureConfigHandler config, String path, String fallback) {
        String value = config.get(path, String.class, fallback);
        return value == null ? fallback : value.trim();
    }

    private static Money positiveMoney(FeatureConfigHandler config, String path, String fallback) {
        Money money = Money.parse(text(config, path, fallback));
        if (!money.isPositive()) {
            throw new IllegalArgumentException(path + " must be positive");
        }
        return money;
    }

    private static Money nonNegativeMoney(FeatureConfigHandler config, String path, String fallback) {
        Money money = Money.parse(text(config, path, fallback));
        if (money.amount().signum() < 0) {
            throw new IllegalArgumentException(path + " cannot be negative");
        }
        return money;
    }

    private static BigDecimal decimal(FeatureConfigHandler config, String path, String fallback) {
        try {
            return new BigDecimal(text(config, path, fallback));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal at " + path, exception);
        }
    }

    private static int integer(
            FeatureConfigHandler config,
            String path,
            int fallback,
            int minimum,
            int maximum
    ) {
        int value = config.get(path, Integer.class, fallback);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static Duration duration(
            FeatureConfigHandler config,
            String path,
            String fallback,
            Duration minimum,
            Duration maximum
    ) {
        Duration value = parseDuration(text(config, path, fallback), path);
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public static Duration parseDuration(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(path + " cannot be blank");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier;
        String number;
        if (value.endsWith("ms")) {
            multiplier = 1L;
            number = value.substring(0, value.length() - 2);
        } else {
            char unit = value.charAt(value.length() - 1);
            number = value.substring(0, value.length() - 1);
            multiplier = switch (unit) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> throw new IllegalArgumentException("Invalid duration unit at " + path + ": " + raw);
            };
        }
        try {
            return Duration.ofMillis(Math.multiplyExact(Long.parseLong(number.trim()), multiplier));
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid duration at " + path + ": " + raw, exception);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + path + ": " + value, exception);
        }
    }
}
