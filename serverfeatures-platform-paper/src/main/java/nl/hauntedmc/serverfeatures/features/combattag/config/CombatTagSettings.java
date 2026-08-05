package nl.hauntedmc.serverfeatures.features.combattag.config;

import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record CombatTagSettings(
        TaggingSettings tagging,
        AttributionSettings attribution,
        LifecycleSettings lifecycle,
        TeleportSettings teleport,
        LogoutSettings logout,
        DisplaySettings display,
        FeedbackSettings feedback
) {

    private static final int MAX_DURATION_SECONDS = 3_600;
    private static final int MAX_ACTION_BAR_SEGMENTS = 50;
    private static final long MAX_FEEDBACK_COOLDOWN_MILLIS = 60_000L;

    public CombatTagSettings {
        Objects.requireNonNull(tagging, "tagging");
        Objects.requireNonNull(attribution, "attribution");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(teleport, "teleport");
        Objects.requireNonNull(logout, "logout");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(feedback, "feedback");
    }

    public static CombatTagSettings load(FeatureConfigHandler config) {
        TaggingSettings tagging = new TaggingSettings(
                enumValue(config, "tagging.mode", TagMode.class, TagMode.BOTH),
                boundedInt(
                        config.get("tagging.duration-seconds", Integer.class, 15),
                        1,
                        MAX_DURATION_SECONDS,
                        "tagging.duration-seconds"
                ),
                config.get("tagging.allow-self-combat", Boolean.class, false),
                worldRule(config, "tagging.worlds")
        );

        AttributionSettings attribution = new AttributionSettings(
                config.get("attribution.link-tamed-pets", Boolean.class, true),
                new ProjectileSettings(
                        config.get("attribution.projectiles.enabled", Boolean.class, true),
                        enumSet(
                                config,
                                "attribution.projectiles.ignored-types",
                                EntityType.class,
                                List.of("EGG", "ENDER_PEARL", "SNOWBALL")
                        )
                ),
                config.get("attribution.link-fishing-hooks", Boolean.class, true),
                config.get("attribution.link-primed-tnt", Boolean.class, true),
                enumSet(
                        config,
                        "attribution.mob-spawn-exclusions",
                        CreatureSpawnEvent.SpawnReason.class,
                        List.of("SPAWNER")
                )
        );

        LifecycleSettings lifecycle = new LifecycleSettings(
                config.get("lifecycle.clear-on-player-death", Boolean.class, true),
                config.get("lifecycle.clear-when-opponent-dies", Boolean.class, true)
        );

        TeleportSettings teleport = new TeleportSettings(
                config.get("teleport.prevent-portals", Boolean.class, true),
                config.get("teleport.prevent-other-teleports", Boolean.class, true),
                enumSet(
                        config,
                        "teleport.allowed-causes",
                        PlayerTeleportEvent.TeleportCause.class,
                        List.of("PLUGIN", "UNKNOWN", "ENDER_PEARL")
                ),
                config.get("teleport.ender-pearl-resets-timer", Boolean.class, false),
                config.get("teleport.clear-after-allowed-teleport", Boolean.class, false)
        );

        LogoutSettings logout = new LogoutSettings(
                config.get("logout-punishment.enabled", Boolean.class, true),
                config.get("logout-punishment.kill-player", Boolean.class, true),
                config.get("logout-punishment.broadcast", Boolean.class, true),
                config.get("logout-punishment.punish-kicked-players", Boolean.class, false),
                immutableCommands(config.getList("logout-punishment.commands", String.class, List.of()))
        );

        DisplaySettings display = new DisplaySettings(
                config.get("display.chat.enter", Boolean.class, true),
                config.get("display.chat.exit", Boolean.class, true),
                new ActionBarSettings(
                        config.get("display.action-bar.enabled", Boolean.class, true),
                        boundedInt(
                                config.get("display.action-bar.update-interval-ticks", Integer.class, 5),
                                1,
                                20,
                                "display.action-bar.update-interval-ticks"
                        ),
                        boundedInt(
                                config.get("display.action-bar.segments", Integer.class, 20),
                                5,
                                MAX_ACTION_BAR_SEGMENTS,
                                "display.action-bar.segments"
                        ),
                        symbol(config, "display.action-bar.filled-symbol", "█"),
                        symbol(config, "display.action-bar.empty-symbol", "█")
                )
        );

        FeedbackSettings feedback = new FeedbackSettings(millisecondsToNanos(boundedLong(
                config.get("feedback.restriction-message-cooldown-millis", Long.class, 1_000L),
                0L,
                MAX_FEEDBACK_COOLDOWN_MILLIS,
                "feedback.restriction-message-cooldown-millis"
        )));

        return new CombatTagSettings(tagging, attribution, lifecycle, teleport, logout, display, feedback);
    }

    private static List<String> immutableCommands(List<String> configured) {
        Objects.requireNonNull(configured, "configured");
        List<String> commands = new ArrayList<>(configured.size());
        for (String command : configured) {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("logout-punishment.commands cannot contain blank commands");
            }
            String normalized = command.trim();
            commands.add(normalized.startsWith("/") ? normalized.substring(1) : normalized);
        }
        return List.copyOf(commands);
    }

    private static String symbol(FeatureConfigHandler config, String key, String fallback) {
        String value = config.get(key, String.class, fallback);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " cannot be blank");
        }
        String trimmed = value.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > 4) {
            throw new IllegalArgumentException(key + " cannot contain more than four characters");
        }
        return trimmed;
    }

    private static WorldRule worldRule(FeatureConfigHandler config, String prefix) {
        WorldMode mode = enumValue(config, prefix + ".mode", WorldMode.class, WorldMode.ALL);
        Set<String> worlds = new HashSet<>();
        for (String configured : config.getList(prefix + ".values", String.class, List.of())) {
            if (configured == null || configured.isBlank()) {
                throw new IllegalArgumentException(prefix + ".values cannot contain blank world names");
            }
            worlds.add(normalizeWorld(configured));
        }
        return new WorldRule(mode, worlds);
    }

    private static <E extends Enum<E>> E enumValue(
            FeatureConfigHandler config,
            String key,
            Class<E> enumType,
            E fallback
    ) {
        String configured = config.get(key, String.class, fallback.name());
        try {
            return Enum.valueOf(enumType, configured.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown value in " + key + ": " + configured, exception);
        }
    }

    private static <E extends Enum<E>> Set<E> enumSet(
            FeatureConfigHandler config,
            String key,
            Class<E> enumType,
            List<String> defaults
    ) {
        EnumSet<E> values = EnumSet.noneOf(enumType);
        for (String configured : config.getList(key, String.class, defaults)) {
            if (configured == null || configured.isBlank()) {
                throw new IllegalArgumentException(key + " cannot contain blank values");
            }
            try {
                values.add(Enum.valueOf(enumType, configured.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unknown value in " + key + ": " + configured, exception);
            }
        }
        return Set.copyOf(values);
    }

    private static int boundedInt(int value, int minimum, int maximum, String key) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static long boundedLong(long value, long minimum, long maximum, String key) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static long millisecondsToNanos(long milliseconds) {
        try {
            return Math.multiplyExact(milliseconds, 1_000_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "feedback.restriction-message-cooldown-millis is too large",
                    exception
            );
        }
    }

    private static String normalizeWorld(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record TaggingSettings(
            TagMode mode,
            int durationSeconds,
            boolean allowSelfCombat,
            WorldRule worlds
    ) {
        public TaggingSettings {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(worlds, "worlds");
        }

        public boolean pvpEnabled() {
            return mode == TagMode.PVP || mode == TagMode.BOTH;
        }

        public boolean mobsEnabled() {
            return mode == TagMode.MOBS || mode == TagMode.BOTH;
        }
    }

    public record AttributionSettings(
            boolean linkTamedPets,
            ProjectileSettings projectiles,
            boolean linkFishingHooks,
            boolean linkPrimedTnt,
            Set<CreatureSpawnEvent.SpawnReason> mobSpawnExclusions
    ) {
        public AttributionSettings {
            Objects.requireNonNull(projectiles, "projectiles");
            mobSpawnExclusions = Set.copyOf(Objects.requireNonNull(
                    mobSpawnExclusions,
                    "mobSpawnExclusions"
            ));
        }
    }

    public record ProjectileSettings(boolean enabled, Set<EntityType> ignoredTypes) {
        public ProjectileSettings {
            ignoredTypes = Set.copyOf(Objects.requireNonNull(ignoredTypes, "ignoredTypes"));
        }
    }

    public record LifecycleSettings(boolean clearOnPlayerDeath, boolean clearWhenOpponentDies) {
    }

    public record TeleportSettings(
            boolean preventPortals,
            boolean preventOtherTeleports,
            Set<PlayerTeleportEvent.TeleportCause> allowedCauses,
            boolean enderPearlResetsTimer,
            boolean clearAfterAllowedTeleport
    ) {
        public TeleportSettings {
            allowedCauses = Set.copyOf(Objects.requireNonNull(allowedCauses, "allowedCauses"));
        }
    }

    public static final class LogoutSettings {
        private final boolean enabled;
        private final boolean killPlayer;
        private final boolean broadcast;
        private final boolean punishKickedPlayers;
        private final List<String> commands;

        public LogoutSettings(
                boolean enabled,
                boolean killPlayer,
                boolean broadcast,
                boolean punishKickedPlayers,
                List<String> commands
        ) {
            this.enabled = enabled;
            this.killPlayer = killPlayer;
            this.broadcast = broadcast;
            this.punishKickedPlayers = punishKickedPlayers;
            this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean killPlayer() {
            return killPlayer;
        }

        public boolean broadcast() {
            return broadcast;
        }

        public boolean punishKickedPlayers() {
            return punishKickedPlayers;
        }

        public List<String> commands() {
            return List.copyOf(commands);
        }
    }

    public record DisplaySettings(
            boolean chatEnter,
            boolean chatExit,
            ActionBarSettings actionBar
    ) {
        public DisplaySettings {
            Objects.requireNonNull(actionBar, "actionBar");
        }
    }

    public record ActionBarSettings(
            boolean enabled,
            int updateIntervalTicks,
            int segments,
            String filledSymbol,
            String emptySymbol
    ) {
        public ActionBarSettings {
            Objects.requireNonNull(filledSymbol, "filledSymbol");
            Objects.requireNonNull(emptySymbol, "emptySymbol");
        }
    }

    public record FeedbackSettings(long restrictionMessageCooldownNanos) {
    }

    public static final class WorldRule {
        private final WorldMode mode;
        private final Set<String> values;

        public WorldRule(WorldMode mode, Set<String> values) {
            this.mode = Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(values, "values");
            Set<String> normalized = new HashSet<>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("World rule values cannot contain blank names");
                }
                normalized.add(normalizeWorld(value));
            }
            this.values = Set.copyOf(normalized);
        }

        public WorldMode mode() {
            return mode;
        }

        public Set<String> values() {
            return Set.copyOf(values);
        }

        public boolean allows(World world) {
            Objects.requireNonNull(world, "world");
            if (mode == WorldMode.ALL) {
                return true;
            }
            boolean listed = values.contains(normalizeWorld(world.getName()));
            return mode == WorldMode.BLACKLIST ? !listed : listed;
        }
    }

    public enum TagMode {
        PVP,
        MOBS,
        BOTH
    }

    public enum WorldMode {
        ALL,
        BLACKLIST,
        WHITELIST
    }
}
