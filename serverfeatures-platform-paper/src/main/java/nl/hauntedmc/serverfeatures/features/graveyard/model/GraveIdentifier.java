package nl.hauntedmc.serverfeatures.features.graveyard.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds compact, human-readable command identifiers for graves.
 */
public final class GraveIdentifier {
    public static final int MAXIMUM_LENGTH = 25;
    private static final int MAXIMUM_OWNER_LENGTH = 16;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("HH:mm:ss", Locale.ROOT);

    private GraveIdentifier() {
    }

    public static String create(String ownerName, long createdWallMillis) {
        return create(ownerName, createdWallMillis, ZoneId.systemDefault());
    }

    static String create(
            String ownerName,
            long createdWallMillis,
            ZoneId zoneId
    ) {
        String owner = owner(ownerName);
        String time = TIMESTAMP.withZone(Objects.requireNonNull(zoneId, "zoneId"))
                .format(Instant.ofEpochMilli(createdWallMillis));
        return owner + "-" + time;
    }

    private static String owner(String value) {
        String normalized = Objects.requireNonNullElse(value, "")
                .trim()
                .replaceAll("[^A-Za-z0-9_]", "");
        if (normalized.isEmpty()) {
            normalized = "player";
        }
        return normalized.substring(0, Math.min(normalized.length(), MAXIMUM_OWNER_LENGTH));
    }
}
