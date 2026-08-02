package nl.hauntedmc.serverfeatures.features.graveyard.config;

import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GraveyardSettings {
    private static final long DEFAULT_LIFETIME_MILLIS = 1_800_000L;
    private static final long DEFAULT_LEASE_MILLIS = 20_000L;
    private static final long DEFAULT_LEASE_HEARTBEAT_MILLIS = 5_000L;

    private final GraveyardMode mode;
    private final String serverId;
    private final String inventoryScope;
    private final long lifetimeMillis;
    private final long leaseDurationMillis;
    private final long leaseHeartbeatMillis;
    private final int experiencePercentage;
    private final int horizontalSearchRadius;
    private final int verticalSearchBelow;
    private final int verticalSearchAbove;
    private final long lastSafeMaxAgeMillis;
    private final double spawnDistance;
    private final double despawnDistance;
    private final int maxRenderedPerViewer;
    private final long reconciliationTicks;
    private final long spawnSettleTicks;
    private final double interactionDistance;
    private final boolean requireLineOfSight;
    private final boolean partialClaims;
    private final int maximumEntries;
    private final int maximumItemBytes;
    private final int maximumPayloadBytes;
    private final Set<String> disabledWorlds;
    private final Set<GameMode> disabledGameModes;
    private final Material baseMaterial;
    private final Material headstoneMaterial;
    private final Particle claimParticle;
    private final Particle expiryParticle;
    private final Sound claimSound;
    private final Sound expirySound;
    private final int ownerGlowRgb;
    private final int otherGlowRgb;
    private final int staffGlowRgb;

    private GraveyardSettings(Graveyard feature) {
        mode = enumSetting(feature, "mode", GraveyardMode.class, GraveyardMode.ACTIVE);
        serverId = normalize(feature.getConfigHandler().getGlobalSetting("server_name", String.class, "server"));
        inventoryScope = normalize(feature.getConfigHandler().get("identity.inventory_scope", String.class, serverId));
        lifetimeMillis = duration(feature, "lifetime.duration", DEFAULT_LIFETIME_MILLIS);
        leaseDurationMillis = duration(feature, "identity.lease_timeout", DEFAULT_LEASE_MILLIS);
        leaseHeartbeatMillis = Math.min(
                leaseDurationMillis / 2L,
                duration(feature, "identity.lease_heartbeat", DEFAULT_LEASE_HEARTBEAT_MILLIS)
        );
        experiencePercentage = clamp(feature.getConfigHandler().get("experience.recovery_percentage", Integer.class, 50), 0, 100);
        horizontalSearchRadius = clamp(feature.getConfigHandler().get("placement.horizontal_search_radius", Integer.class, 8), 0, 32);
        verticalSearchBelow = clamp(feature.getConfigHandler().get("placement.vertical_search_below", Integer.class, 4), 0, 16);
        verticalSearchAbove = clamp(feature.getConfigHandler().get("placement.vertical_search_above", Integer.class, 6), 0, 16);
        lastSafeMaxAgeMillis = duration(feature, "placement.last_safe_location_max_age", 30_000L);
        spawnDistance = positive(feature.getConfigHandler().get("render.spawn_distance", Double.class, 48.0), 48.0);
        despawnDistance = Math.max(spawnDistance, positive(
                feature.getConfigHandler().get("render.despawn_distance", Double.class, 56.0),
                56.0
        ));
        maxRenderedPerViewer = clamp(feature.getConfigHandler().get("render.max_rendered_per_viewer", Integer.class, 64), 1, 512);
        reconciliationTicks = clamp(feature.getConfigHandler().get("render.reconciliation_interval_ticks", Long.class, 20L), 1L, 200L);
        spawnSettleTicks = clamp(feature.getConfigHandler().get("render.spawn_settle_delay_ticks", Long.class, 2L), 0L, 100L);
        interactionDistance = positive(feature.getConfigHandler().get("interaction.maximum_distance", Double.class, 4.5), 4.5);
        requireLineOfSight = feature.getConfigHandler().get("interaction.require_line_of_sight", Boolean.class, true);
        partialClaims = feature.getConfigHandler().get("claim.partial_claims", Boolean.class, true);
        maximumEntries = clamp(feature.getConfigHandler().get("storage.payload.maximum_entries", Integer.class, 64), 1, 256);
        maximumItemBytes = clamp(feature.getConfigHandler().get("storage.payload.maximum_item_bytes", Integer.class, 2_097_152), 1_024, 8_388_608);
        maximumPayloadBytes = clamp(feature.getConfigHandler().get("storage.payload.maximum_total_bytes", Integer.class, 8_388_608), maximumItemBytes, 33_554_432);
        disabledWorlds = normalizeWorlds(feature.getConfigHandler().getList("eligibility.disabled_worlds", String.class, List.of()));
        disabledGameModes = parseGameModes(feature.getConfigHandler().getList(
                "eligibility.disabled_gamemodes",
                String.class,
                List.of("CREATIVE", "SPECTATOR")
        ));
        baseMaterial = material(feature, "render.base.material", Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        headstoneMaterial = material(feature, "render.headstone.material", Material.POLISHED_BLACKSTONE_BRICK_WALL);
        claimParticle = particle(feature, "particles.claim.type", Particle.SCULK_SOUL);
        expiryParticle = particle(feature, "particles.expiry.type", Particle.SCULK_SOUL);
        claimSound = sound(feature, "sounds.claim.sound", Sound.BLOCK_RESPAWN_ANCHOR_CHARGE);
        expirySound = sound(feature, "sounds.expiry.sound", Sound.PARTICLE_SOUL_ESCAPE);
        ownerGlowRgb = rgb(feature.getConfigHandler().get("render.glow.owner_rgb", String.class, "55FFFF"), 0x55FFFF);
        otherGlowRgb = rgb(feature.getConfigHandler().get("render.glow.other_rgb", String.class, "00AAAA"), 0x00AAAA);
        staffGlowRgb = rgb(feature.getConfigHandler().get("render.glow.staff_rgb", String.class, "FFD700"), 0xFFD700);
    }

    public static GraveyardSettings load(Graveyard feature) {
        return new GraveyardSettings(feature);
    }

    public GraveyardMode mode() { return mode; }
    public String serverId() { return serverId; }
    public String inventoryScope() { return inventoryScope; }
    public long lifetimeMillis() { return lifetimeMillis; }
    public long leaseDurationMillis() { return leaseDurationMillis; }
    public long leaseHeartbeatMillis() { return leaseHeartbeatMillis; }
    public int experiencePercentage() { return experiencePercentage; }
    public int horizontalSearchRadius() { return horizontalSearchRadius; }
    public int verticalSearchBelow() { return verticalSearchBelow; }
    public int verticalSearchAbove() { return verticalSearchAbove; }
    public long lastSafeMaxAgeMillis() { return lastSafeMaxAgeMillis; }
    public double spawnDistance() { return spawnDistance; }
    public double despawnDistance() { return despawnDistance; }
    public int maxRenderedPerViewer() { return maxRenderedPerViewer; }
    public long reconciliationTicks() { return reconciliationTicks; }
    public long spawnSettleTicks() { return spawnSettleTicks; }
    public double interactionDistance() { return interactionDistance; }
    public boolean requireLineOfSight() { return requireLineOfSight; }
    public boolean partialClaims() { return partialClaims; }
    public int maximumEntries() { return maximumEntries; }
    public int maximumItemBytes() { return maximumItemBytes; }
    public int maximumPayloadBytes() { return maximumPayloadBytes; }
    public Set<String> disabledWorlds() { return disabledWorlds; }
    public Set<GameMode> disabledGameModes() { return disabledGameModes; }
    public Material baseMaterial() { return baseMaterial; }
    public Material headstoneMaterial() { return headstoneMaterial; }
    public Particle claimParticle() { return claimParticle; }
    public Particle expiryParticle() { return expiryParticle; }
    public Sound claimSound() { return claimSound; }
    public Sound expirySound() { return expirySound; }
    public int ownerGlowRgb() { return ownerGlowRgb; }
    public int otherGlowRgb() { return otherGlowRgb; }
    public int staffGlowRgb() { return staffGlowRgb; }

    private static long duration(Graveyard feature, String key, long fallback) {
        return DurationValueParser.parseMillis(feature.getConfigHandler().get(key), fallback);
    }

    private static Material material(Graveyard feature, String key, Material fallback) {
        return enumSetting(feature, key, Material.class, fallback);
    }

    private static Particle particle(Graveyard feature, String key, Particle fallback) {
        return enumSetting(feature, key, Particle.class, fallback);
    }

    private static Sound sound(Graveyard feature, String key, Sound fallback) {
        return enumSetting(feature, key, Sound.class, fallback);
    }

    private static <E extends Enum<E>> E enumSetting(
            Graveyard feature,
            String key,
            Class<E> type,
            E fallback
    ) {
        String configured = feature.getConfigHandler().get(key, String.class, fallback.name());
        try {
            return Enum.valueOf(type, configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            feature.getLogger().warning("Invalid Graveyard setting " + key + "=" + configured + "; using " + fallback);
            return fallback;
        }
    }

    private static Set<GameMode> parseGameModes(List<String> values) {
        Set<GameMode> result = EnumSet.noneOf(GameMode.class);
        for (String value : values) {
            try {
                result.add(GameMode.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Invalid optional values are ignored after the typed settings object is created.
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> normalizeWorlds(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_.:-]", "_").replaceAll("_+", "_");
        return normalized.isBlank() ? "server" : normalized.substring(0, Math.min(normalized.length(), 100));
    }

    private static int rgb(String value, int fallback) {
        String normalized = value == null ? "" : value.trim().replace("#", "");
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
