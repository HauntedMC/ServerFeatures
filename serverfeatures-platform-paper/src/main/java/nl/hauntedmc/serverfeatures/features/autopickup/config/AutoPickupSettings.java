package nl.hauntedmc.serverfeatures.features.autopickup.config;

import nl.hauntedmc.serverfeatures.features.autopickup.model.DropScope;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record AutoPickupSettings(
        boolean defaultEnabled,
        DropScope dropScope,
        WorldMode worldMode,
        Set<String> worlds,
        Set<GameMode> allowedGameModes,
        boolean requireUsePermission,
        NotificationSettings notification,
        PickupSoundSettings pickupSound,
        RetrySettings retry,
        long joinRecheckDelayMillis,
        long shutdownDrainTimeoutMillis,
        long diagnosticWarningCooldownNanos
) {

    private static final int MAX_NOTIFICATION_DURATION_SECONDS = 60;
    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final long MAX_RETRY_DELAY_MILLIS = 60_000L;
    private static final long MAX_JOIN_RECHECK_DELAY_MILLIS = 60_000L;
    private static final long MAX_SHUTDOWN_DRAIN_TIMEOUT_MILLIS = 10_000L;
    private static final float MAX_PICKUP_SOUND_VOLUME = 16.0F;

    public AutoPickupSettings {
        Objects.requireNonNull(dropScope, "dropScope");
        Objects.requireNonNull(worldMode, "worldMode");
        worlds = Set.copyOf(worlds);
        allowedGameModes = Set.copyOf(allowedGameModes);
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(pickupSound, "pickupSound");
        Objects.requireNonNull(retry, "retry");
        validateRange(
                joinRecheckDelayMillis,
                0L,
                MAX_JOIN_RECHECK_DELAY_MILLIS,
                "joinRecheckDelayMillis"
        );
        validateRange(
                shutdownDrainTimeoutMillis,
                0L,
                MAX_SHUTDOWN_DRAIN_TIMEOUT_MILLIS,
                "shutdownDrainTimeoutMillis"
        );
        if (diagnosticWarningCooldownNanos < 0L) {
            throw new IllegalArgumentException("diagnosticWarningCooldownNanos cannot be negative");
        }
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
        boolean requireUsePermission = config.get("drop-policy.require-use-permission", Boolean.class, true);

        boolean notificationEnabled = config.get("notification.inventory-full.enabled", Boolean.class, true);
        boolean notifyOnPartial = config.get("notification.inventory-full.notify-on-partial", Boolean.class, true);
        long cooldownMillis = nonNegative(
                config.get("notification.inventory-full.cooldown-millis", Long.class, 3000L),
                "notification.inventory-full.cooldown-millis"
        );
        int durationSeconds = boundedNonNegativeInt(
                config.get("notification.inventory-full.duration-seconds", Integer.class, 2),
                MAX_NOTIFICATION_DURATION_SECONDS,
                "notification.inventory-full.duration-seconds"
        );

        boolean pickupSoundEnabled = config.get("effects.pickup-sound.enabled", Boolean.class, true);
        String pickupSoundKey = parseSoundKey(config.get(
                "effects.pickup-sound.sound",
                String.class,
                "minecraft:entity.item.pickup"
        ));
        SoundCategory pickupSoundCategory = parseSoundCategory(config.get(
                "effects.pickup-sound.category",
                String.class,
                "PLAYERS"
        ));
        float pickupSoundVolume = finiteRange(
                config.get("effects.pickup-sound.volume", Double.class, 0.2D),
                0.0D,
                MAX_PICKUP_SOUND_VOLUME,
                "effects.pickup-sound.volume"
        );
        float pickupSoundPitch = finiteRange(
                config.get("effects.pickup-sound.pitch", Double.class, 1.0D),
                0.5D,
                2.0D,
                "effects.pickup-sound.pitch"
        );

        int attempts = config.get("persistence.retry.attempts", Integer.class, 3);
        if (attempts < 1 || attempts > MAX_RETRY_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "persistence.retry.attempts must be between 1 and " + MAX_RETRY_ATTEMPTS
            );
        }
        long initialDelay = boundedNonNegative(
                config.get("persistence.retry.initial-delay-millis", Long.class, 250L),
                MAX_RETRY_DELAY_MILLIS,
                "persistence.retry.initial-delay-millis"
        );
        long maximumDelay = boundedNonNegative(
                config.get("persistence.retry.maximum-delay-millis", Long.class, 2000L),
                MAX_RETRY_DELAY_MILLIS,
                "persistence.retry.maximum-delay-millis"
        );
        if (maximumDelay < initialDelay) {
            throw new IllegalArgumentException(
                    "persistence.retry.maximum-delay-millis must be at least the initial delay"
            );
        }

        long joinRecheckDelay = boundedNonNegative(
                config.get("persistence.join-recheck-delay-millis", Long.class, 3000L),
                MAX_JOIN_RECHECK_DELAY_MILLIS,
                "persistence.join-recheck-delay-millis"
        );
        long drainTimeout = boundedNonNegative(
                config.get("persistence.shutdown-drain-timeout-millis", Long.class, 1000L),
                MAX_SHUTDOWN_DRAIN_TIMEOUT_MILLIS,
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
                requireUsePermission,
                new NotificationSettings(
                        notificationEnabled,
                        notifyOnPartial,
                        millisecondsToNanos(cooldownMillis, "notification.inventory-full.cooldown-millis"),
                        durationSeconds
                ),
                new PickupSoundSettings(
                        pickupSoundEnabled,
                        pickupSoundKey,
                        pickupSoundCategory,
                        pickupSoundVolume,
                        pickupSoundPitch
                ),
                new RetrySettings(attempts, initialDelay, maximumDelay),
                joinRecheckDelay,
                drainTimeout,
                millisecondsToNanos(diagnosticCooldownMillis, "diagnostics.warning-cooldown-millis")
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

    private static String parseSoundKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("effects.pickup-sound.sound cannot be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) {
            throw new IllegalArgumentException("Invalid effects.pickup-sound.sound key: " + value);
        }
        return key.toString();
    }

    private static SoundCategory parseSoundCategory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("effects.pickup-sound.category cannot be blank");
        }
        try {
            return SoundCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown effects.pickup-sound.category: " + value,
                    exception
            );
        }
    }

    private static long nonNegative(long value, String key) {
        if (value < 0L) {
            throw new IllegalArgumentException(key + " cannot be negative");
        }
        return value;
    }

    private static int boundedNonNegativeInt(int value, int maximum, String key) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(key + " must be between 0 and " + maximum);
        }
        return value;
    }

    private static long boundedNonNegative(long value, long maximum, String key) {
        if (value < 0L || value > maximum) {
            throw new IllegalArgumentException(key + " must be between 0 and " + maximum);
        }
        return value;
    }

    private static void validateRange(long value, long minimum, long maximum, String key) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    key + " must be between " + minimum + " and " + maximum
            );
        }
    }

    private static float finiteRange(double value, double minimum, double maximum, String key) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    key + " must be finite and between " + minimum + " and " + maximum
            );
        }
        return (float) value;
    }

    private static long millisecondsToNanos(long millis, String key) {
        try {
            return Math.multiplyExact(millis, 1_000_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(key + " is too large", exception);
        }
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
        public NotificationSettings {
            if (cooldownNanos < 0L) {
                throw new IllegalArgumentException("cooldownNanos cannot be negative");
            }
            if (durationSeconds < 0 || durationSeconds > MAX_NOTIFICATION_DURATION_SECONDS) {
                throw new IllegalArgumentException(
                        "durationSeconds must be between 0 and " + MAX_NOTIFICATION_DURATION_SECONDS
                );
            }
        }
    }

    public record PickupSoundSettings(
            boolean enabled,
            String soundKey,
            SoundCategory category,
            float volume,
            float pitch
    ) {
        public PickupSoundSettings {
            if (soundKey == null || soundKey.isBlank()) {
                throw new IllegalArgumentException("soundKey cannot be blank");
            }
            Objects.requireNonNull(category, "category");
            if (!Float.isFinite(volume) || volume < 0.0F || volume > MAX_PICKUP_SOUND_VOLUME) {
                throw new IllegalArgumentException(
                        "volume must be finite and between 0.0 and " + MAX_PICKUP_SOUND_VOLUME
                );
            }
            if (!Float.isFinite(pitch) || pitch < 0.5F || pitch > 2.0F) {
                throw new IllegalArgumentException("pitch must be finite and between 0.5 and 2.0");
            }
        }
    }

    public record RetrySettings(int attempts, long initialDelayMillis, long maximumDelayMillis) {
        public RetrySettings {
            if (attempts < 1 || attempts > MAX_RETRY_ATTEMPTS) {
                throw new IllegalArgumentException(
                        "attempts must be between 1 and " + MAX_RETRY_ATTEMPTS
                );
            }
            validateRange(initialDelayMillis, 0L, MAX_RETRY_DELAY_MILLIS, "initialDelayMillis");
            validateRange(maximumDelayMillis, 0L, MAX_RETRY_DELAY_MILLIS, "maximumDelayMillis");
            if (maximumDelayMillis < initialDelayMillis) {
                throw new IllegalArgumentException(
                        "maximumDelayMillis must be at least initialDelayMillis"
                );
            }
        }

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
