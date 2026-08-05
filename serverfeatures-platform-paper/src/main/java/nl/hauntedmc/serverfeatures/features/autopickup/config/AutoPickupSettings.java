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
        long diagnosticWarningCooldownNanos
) {

    private static final int MAX_NOTIFICATION_DURATION_SECONDS = 60;
    private static final float MAX_PICKUP_SOUND_VOLUME = 16.0F;

    public AutoPickupSettings {
        Objects.requireNonNull(dropScope, "dropScope");
        Objects.requireNonNull(worldMode, "worldMode");
        worlds = Set.copyOf(worlds);
        allowedGameModes = Set.copyOf(allowedGameModes);
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(pickupSound, "pickupSound");
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

}
