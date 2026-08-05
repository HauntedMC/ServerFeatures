package nl.hauntedmc.serverfeatures.features.fairperks.config;

import nl.hauntedmc.serverfeatures.api.util.text.TextPatterns;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockIgniteEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record FairPerksSettings(
        CommandSettings commands,
        WorldRule worlds,
        FlightSettings flight,
        GodSettings god,
        ActivationGuardSettings activationGuard,
        RestrictionSettings restrictions,
        HostileSettings hostiles,
        GodMacroSettings godMacro,
        FeedbackSettings feedback
) {

    private static final int MAX_RADIUS = 128;
    private static final long MAX_FEEDBACK_COOLDOWN_MILLIS = 60_000L;
    private static final long MAX_MACRO_INTERVAL_MILLIS = 5_000L;

    public FairPerksSettings {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(worlds, "worlds");
        Objects.requireNonNull(flight, "flight");
        Objects.requireNonNull(god, "god");
        Objects.requireNonNull(activationGuard, "activationGuard");
        Objects.requireNonNull(restrictions, "restrictions");
        Objects.requireNonNull(hostiles, "hostiles");
        Objects.requireNonNull(godMacro, "godMacro");
        Objects.requireNonNull(feedback, "feedback");
    }

    public static FairPerksSettings load(FeatureConfigHandler config) {
        CommandSettings commands = new CommandSettings(
                aliases(config, "commands.fly-aliases"),
                aliases(config, "commands.god-aliases"),
                aliases(config, "commands.godmacro-aliases")
        );

        WorldRule worlds = worldRule(config, "worlds", WorldMode.ALL);

        FlightSettings flight = new FlightSettings(
                config.get("flight.enable-starts-flying", Boolean.class, true),
                gameModes(config, "flight.allowed-game-modes", List.of("SURVIVAL", "ADVENTURE")),
                worldRule(config, "flight.worlds", WorldMode.BLACKLIST),
                config.get("flight.persistence.enabled", Boolean.class, true),
                config.get("flight.persistence.restore-active-flight", Boolean.class, true),
                config.get("flight.persistence.restore-when-airborne", Boolean.class, true),
                config.get("flight.revocation.cancel-next-fall-damage", Boolean.class, true)
        );

        GodSettings god = new GodSettings(
                gameModes(
                        config,
                        "god.allowed-game-modes",
                        List.of("SURVIVAL", "ADVENTURE", "CREATIVE", "SPECTATOR")
                ),
                worldRule(config, "god.worlds", WorldMode.BLACKLIST),
                config.get("god.persistence.enabled", Boolean.class, true),
                config.get("god.damage.protect-void", Boolean.class, false)
        );

        ActivationGuardSettings activationGuard = new ActivationGuardSettings(
                config.get("activation-guard.combat.enabled", Boolean.class, true),
                config.get("activation-guard.hostile-nearby.enabled", Boolean.class, true),
                boundedInt(
                        config.get("activation-guard.hostile-nearby.horizontal-radius", Integer.class, 16),
                        0,
                        MAX_RADIUS,
                        "activation-guard.hostile-nearby.horizontal-radius"
                ),
                boundedInt(
                        config.get("activation-guard.hostile-nearby.vertical-radius", Integer.class, 16),
                        0,
                        MAX_RADIUS,
                        "activation-guard.hostile-nearby.vertical-radius"
                )
        );

        RestrictionSettings restrictions = new RestrictionSettings(
                config.get("restrictions.pvp", Boolean.class, true),
                config.get("restrictions.tamed-pet-damage", Boolean.class, true),
                config.get("restrictions.hostile-melee", Boolean.class, true),
                config.get("restrictions.hostile-projectiles", Boolean.class, true),
                config.get("restrictions.hostile-targeting", Boolean.class, true),
                config.get("restrictions.exploding-beds", Boolean.class, true),
                config.get("restrictions.exploding-anchors", Boolean.class, true),
                config.get("restrictions.end-crystals", Boolean.class, true),
                config.get("restrictions.tnt-prime", Boolean.class, true),
                config.get("restrictions.tnt-ignite", Boolean.class, true),
                config.get("restrictions.creeper-ignite", Boolean.class, true),
                config.get("restrictions.lava-near-hostiles", Boolean.class, true),
                config.get("restrictions.block-ignite-near-hostiles", Boolean.class, true),
                boundedInt(
                        config.get("restrictions.nearby-radii.ignite", Integer.class, 5),
                        0,
                        MAX_RADIUS,
                        "restrictions.nearby-radii.ignite"
                ),
                boundedInt(
                        config.get("restrictions.nearby-radii.lava", Integer.class, 5),
                        0,
                        MAX_RADIUS,
                        "restrictions.nearby-radii.lava"
                ),
                boundedInt(
                        config.get("restrictions.nearby-radii.tnt", Integer.class, 10),
                        0,
                        MAX_RADIUS,
                        "restrictions.nearby-radii.tnt"
                ),
                igniteCauses(config)
        );

        HostileSettings hostiles = new HostileSettings(
                entityTypes(config, "hostiles.include"),
                entityTypes(config, "hostiles.exclude"),
                config.get("hostiles.spawner-mobs-exempt", Boolean.class, true),
                config.get("hostiles.mark-spawner-mobs", Boolean.class, false)
        );

        GodMacroSettings godMacro = new GodMacroSettings(
                config.get("god-macro.enabled", Boolean.class, true),
                boundedLong(
                        config.get("god-macro.interval-millis", Long.class, 350L),
                        100L,
                        MAX_MACRO_INTERVAL_MILLIS,
                        "god-macro.interval-millis"
                )
        );

        FeedbackSettings feedback = new FeedbackSettings(millisecondsToNanos(boundedLong(
                config.get("feedback.actionbar-cooldown-millis", Long.class, 1_000L),
                0L,
                MAX_FEEDBACK_COOLDOWN_MILLIS,
                "feedback.actionbar-cooldown-millis"
        )));

        return new FairPerksSettings(
                commands,
                worlds,
                flight,
                god,
                activationGuard,
                restrictions,
                hostiles,
                godMacro,
                feedback
        );
    }

    private static List<String> aliases(FeatureConfigHandler config, String key) {
        List<String> aliases = new ArrayList<>();
        for (String value : config.getList(key, String.class, List.of())) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(key + " cannot contain blank aliases");
            }
            aliases.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(aliases);
    }

    private static List<String> immutableAliases(List<String> aliases, String component) {
        Objects.requireNonNull(aliases, component);
        List<String> normalized = new ArrayList<>(aliases.size());
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException(component + " cannot contain blank aliases");
            }
            String value = alias.trim().toLowerCase(Locale.ROOT);
            if (!TextPatterns.BUKKIT_ALIAS_FORMAT.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid command alias in " + component + ": " + alias);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private static Set<GameMode> gameModes(
            FeatureConfigHandler config,
            String key,
            List<String> defaults
    ) {
        EnumSet<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String configured : config.getList(key, String.class, defaults)) {
            try {
                modes.add(GameMode.valueOf(configured.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unknown game mode in " + key + ": " + configured, exception);
            }
        }
        if (modes.isEmpty()) {
            throw new IllegalArgumentException(key + " cannot be empty");
        }
        return Set.copyOf(modes);
    }

    private static WorldRule worldRule(
            FeatureConfigHandler config,
            String prefix,
            WorldMode defaultMode
    ) {
        WorldMode mode;
        String configuredMode = config.get(prefix + ".mode", String.class, defaultMode.name());
        try {
            mode = WorldMode.valueOf(configuredMode.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Unknown world mode in " + prefix + ".mode: " + configuredMode,
                    exception
            );
        }

        Set<String> values = new HashSet<>();
        for (String configured : config.getList(prefix + ".values", String.class, List.of())) {
            if (configured == null || configured.isBlank()) {
                throw new IllegalArgumentException(prefix + ".values cannot contain blank world names");
            }
            values.add(normalizeWorld(configured));
        }
        return new WorldRule(mode, values);
    }

    private static Set<EntityType> entityTypes(FeatureConfigHandler config, String key) {
        EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String configured : config.getList(key, String.class, List.of())) {
            try {
                types.add(EntityType.valueOf(configured.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unknown entity type in " + key + ": " + configured, exception);
            }
        }
        return Set.copyOf(types);
    }

    private static Set<BlockIgniteEvent.IgniteCause> igniteCauses(FeatureConfigHandler config) {
        EnumSet<BlockIgniteEvent.IgniteCause> causes = EnumSet.noneOf(BlockIgniteEvent.IgniteCause.class);
        for (String configured : config.getList(
                "restrictions.block-ignite-causes",
                String.class,
                List.of("FLINT_AND_STEEL", "FIREBALL")
        )) {
            try {
                causes.add(BlockIgniteEvent.IgniteCause.valueOf(configured.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Unknown ignite cause in restrictions.block-ignite-causes: " + configured,
                        exception
                );
            }
        }
        return Set.copyOf(causes);
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
            throw new IllegalArgumentException("feedback.actionbar-cooldown-millis is too large", exception);
        }
    }

    private static String normalizeWorld(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record CommandSettings(
            List<String> flyAliases,
            List<String> godAliases,
            List<String> godMacroAliases
    ) {
        public CommandSettings {
            flyAliases = immutableAliases(flyAliases, "flyAliases");
            godAliases = immutableAliases(godAliases, "godAliases");
            godMacroAliases = immutableAliases(godMacroAliases, "godMacroAliases");
        }
    }

    public record FlightSettings(
            boolean enableStartsFlying,
            Set<GameMode> allowedGameModes,
            WorldRule worlds,
            boolean persistenceEnabled,
            boolean restoreActiveFlight,
            boolean restoreWhenAirborne,
            boolean cancelNextFallDamageOnRevocation
    ) {
        public FlightSettings {
            allowedGameModes = Set.copyOf(allowedGameModes);
            Objects.requireNonNull(worlds, "worlds");
        }

        public boolean allows(Player player) {
            return allowedGameModes.contains(player.getGameMode()) && worlds.allows(player.getWorld());
        }
    }

    public record GodSettings(
            Set<GameMode> allowedGameModes,
            WorldRule worlds,
            boolean persistenceEnabled,
            boolean protectVoid
    ) {
        public GodSettings {
            allowedGameModes = Set.copyOf(allowedGameModes);
            Objects.requireNonNull(worlds, "worlds");
        }

        public boolean allows(Player player) {
            return allowedGameModes.contains(player.getGameMode()) && worlds.allows(player.getWorld());
        }
    }

    public record ActivationGuardSettings(
            boolean combatEnabled,
            boolean hostileNearbyEnabled,
            int horizontalRadius,
            int verticalRadius
    ) {
    }

    public record RestrictionSettings(
            boolean pvp,
            boolean tamedPetDamage,
            boolean hostileMelee,
            boolean hostileProjectiles,
            boolean hostileTargeting,
            boolean explodingBeds,
            boolean explodingAnchors,
            boolean endCrystals,
            boolean tntPrime,
            boolean tntIgnite,
            boolean creeperIgnite,
            boolean lavaNearHostiles,
            boolean blockIgniteNearHostiles,
            int igniteRadius,
            int lavaRadius,
            int tntRadius,
            Set<BlockIgniteEvent.IgniteCause> blockIgniteCauses
    ) {
        public RestrictionSettings {
            blockIgniteCauses = Set.copyOf(blockIgniteCauses);
        }
    }

    public record HostileSettings(
            Set<EntityType> include,
            Set<EntityType> exclude,
            boolean spawnerMobsExempt,
            boolean markSpawnerMobs
    ) {
        public HostileSettings {
            include = Set.copyOf(include);
            exclude = Set.copyOf(exclude);
        }
    }

    public record GodMacroSettings(boolean enabled, long intervalMillis) {
    }

    public record FeedbackSettings(long actionBarCooldownNanos) {
    }

    public record WorldRule(WorldMode mode, Set<String> values) {
        public WorldRule {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(values, "values");
            Set<String> normalized = new HashSet<>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("World rule values cannot contain blank names");
                }
                normalized.add(normalizeWorld(value));
            }
            values = Set.copyOf(normalized);
        }

        public boolean allows(World world) {
            boolean listed = values.contains(normalizeWorld(world.getName()));
            return switch (mode) {
                case ALL -> true;
                case BLACKLIST -> !listed;
                case WHITELIST -> listed;
            };
        }
    }

    public enum WorldMode {
        ALL,
        BLACKLIST,
        WHITELIST
    }
}
