package nl.hauntedmc.serverfeatures.features.graveyard.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds stable, human-readable command identifiers for graves.
 */
public final class GraveIdentifier {
    public static final int MAXIMUM_LENGTH = 160;
    private static final int MAXIMUM_OWNER_LENGTH = 16;
    private static final int MAXIMUM_WORLD_LENGTH = 100;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    private GraveIdentifier() {
    }

    public static String create(String ownerName, String worldName, long createdWallMillis) {
        return create(ownerName, worldName, createdWallMillis, ZoneId.systemDefault());
    }

    static String create(
            String ownerName,
            String worldName,
            long createdWallMillis,
            ZoneId zoneId
    ) {
        String owner = slug(ownerName, "player", MAXIMUM_OWNER_LENGTH);
        String world = slug(worldName, "world", MAXIMUM_WORLD_LENGTH);
        String time = TIMESTAMP.withZone(Objects.requireNonNull(zoneId, "zoneId"))
                .format(Instant.ofEpochMilli(createdWallMillis));
        String identifier = owner + "-" + world + "-" + time;
        if (identifier.length() <= MAXIMUM_LENGTH) {
            return identifier;
        }
        int maximumWorldLength = Math.max(
                1,
                MAXIMUM_LENGTH - owner.length() - time.length() - 2
        );
        return owner + "-" + world.substring(0, Math.min(world.length(), maximumWorldLength)) + "-" + time;
    }

    private static String slug(String value, String fallback, int maximumLength) {
        String normalized = Objects.requireNonNullElse(value, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.substring(0, Math.min(normalized.length(), maximumLength));
    }
}
