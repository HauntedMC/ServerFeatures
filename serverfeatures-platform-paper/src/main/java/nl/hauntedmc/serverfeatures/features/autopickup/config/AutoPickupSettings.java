package nl.hauntedmc.serverfeatures.features.autopickup.config;

import nl.hauntedmc.serverfeatures.features.autopickup.model.DropScope;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record AutoPickupSettings(
        boolean defaultEnabled,
        DropScope dropScope,
        WorldMode worldMode,
        Set<String> worlds,
        Set<GameMode> allowedGameModes,
        NotificationSettings notification,
        RetrySettings retry,
        long shutdownDrainTimeoutMillis,
        long diagnosticWarningCooldownNanos
) {

    public AutoPickupSettings {
        worlds = Set.copyOf(worlds);
        allowedGameModes = Set.copyOf(allowedGameModes);
    }

    public static AutoPickupSettings load(FeatureConfigHandler config) {
        boolean defaultEnabled = config.get("default-enabled", Boolean.class, false);
        DropScope dropScope = DropScope.parse(config.get("drop-policy.scope", String.class, "STRICT_DIRECT"));
        WorldMode worldMode = WorldMode.parse(config.get("drop-policy.worlds.mode", String.class, "BLACKLIST"));

        Set<String> worlds = new HashSet<>();
        for (String configured : config.getList("drop-policy.worlds.values", String.class, List.of())) {
            if (configured == null || configured.isBlank()) {
                throw new IllegalArgumentException("drop-policy.worlds.values cannot contain blank names");
            }
            worlds.add(normalizeWorld(configured));
        }

        EnumSet<GameMode> gameModes = EnumSet.noneOf(GameMode.class);
        for (String configured : config.getList(
                "drop-policy.allowed-game-modes",
                String.class,
                List.of("SURVIVAL", "ADVENTURE")
        )) {
            try {
                gameModes.add(GameMode.valueOf(configured.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Unknown game mode in drop-policy.allowed-game-modes: " + configured,
                        exception
                );
            }
        }
        if (gameModes.isEmpty()) {
            throw new IllegalArgumentException("drop-policy.allowed-game-modes cannot be empty");
        }

        boolean notificationEnabled = config.get("notification.inventory-full.enabled", Boolean.class, true);
        boolean notifyOnPartial = config.get("notification.inventory-full.notify-on-partial", Boolean.class, true);
        long cooldownMillis = nonNegative(
                config.get("notification.inventory-full.cooldown-millis", Long.class, 3000L),
                "notification.inventory-full.cooldown-millis"
        );
        int durationSeconds = nonNegativeInt(
                config.get("notification.inventory-full.duration-seconds", Integer.class, 2),
                "notification.inventory-full.duration-seconds"
        );

        int attempts = config.get("persistence.retry.attempts", Integer.class, 3);
        if (attempts < 1) {
            throw new IllegalArgumentException("persistence.retry.attempts must be at least 1");
        }
        long initialDelay = nonNegative(
                config.get("persistence.retry.initial-delay-millis", Long.class, 250L),
                "persistence.retry.initial-delay-millis"
        );
        long maximumDelay = nonNegative(
                config.get("persistence.retry.maximum-delay-millis", Long.class, 2000L),
                "persistence.retry.maximum-delay-millis"
        );
        if (maximumDelay < initialDelay) {
            throw new IllegalArgumentException(
                    "persistence.retry.maximum-delay-millis must be at least the initial delay"
            );
        }

        long drainTimeout = nonNegative(
                config.get("persistence.shutdown-drain-timeout-millis", Long.class, 1000L),
                "persistence.shutdown-drain-timeout-millis"
        );
        long diagnosticCooldownMillis = nonNegative(
                config.get("diagnostics.warning-cooldown-millis", Long.class, 30000L),
                "diagnostics.warning-cooldown-millis"
        );

        return new AutoPickupSettings(
                defaultEnabled,
                dropScope,
                worldMode,
                worlds,
                gameModes,
                new NotificationSettings(
                        notificationEnabled,
                        notifyOnPartial,
                        cooldownMillis * 1_000_000L,
                        durationSeconds
                ),
                new RetrySettings(attempts, initialDelay, maximumDelay),
                drainTimeout,
                diagnosticCooldownMillis * 1_000_000L
        );
    }

    public boolean allows(Player player) {
        if (!allowedGameModes.contains(player.getGameMode())) {
            return false;
        }
        World world = player.getWorld();
        boolean listed = worlds.contains(normalizeWorld(world.getName()));
        return worldMode == WorldMode.WHITELIST ? listed : !listed;
    }

    private static String normalizeWorld(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static long nonNegative(long value, String key) {
        if (value < 0L) {
            throw new IllegalArgumentException(key + " cannot be negative");
        }
        return value;
    }

    private static int nonNegativeInt(int value, String key) {
        if (value < 0) {
            throw new IllegalArgumentException(key + " cannot be negative");
        }
        return value;
    }

    public enum WorldMode {
        BLACKLIST,
        WHITELIST;

        static WorldMode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Unknown drop-policy.worlds.mode '" + value + "'. Expected BLACKLIST or WHITELIST.",
                        exception
                );
            }
        }
    }

    public record NotificationSettings(
            boolean enabled,
            boolean notifyOnPartial,
            long cooldownNanos,
            int durationSeconds
    ) {
    }

    public record RetrySettings(int attempts, long initialDelayMillis, long maximumDelayMillis) {
        public long delayForAttempt(int completedAttempts) {
            if (completedAttempts <= 0 || initialDelayMillis == 0L) {
                return initialDelayMillis;
            }
            long multiplier = 1L << Math.min(completedAttempts, 30);
            long calculated;
            try {
                calculated = Math.multiplyExact(initialDelayMillis, multiplier);
            } catch (ArithmeticException ignored) {
                calculated = Long.MAX_VALUE;
            }
            return Math.min(maximumDelayMillis, calculated);
        }
    }
}
