package nl.hauntedmc.serverfeatures.features.votifier.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Mirrors the shape of Votifier's Vote for easy migration.
 */
public record VotePayload(
        String serviceName,
        String username,
        String address,
        long voteTimestamp,
        String processingKey
) {
    public VotePayload {
        processingKey = processingKey == null || processingKey.isBlank()
                ? stableProcessingKey(serviceName, username, address, voteTimestamp)
                : processingKey;
        address = address == null ? "-" : address;
    }

    public VotePayload(String serviceName, String username, String address, long voteTimestamp) {
        this(serviceName, username, address, voteTimestamp, null);
    }

    public String getTimeStamp() {
        return String.valueOf(voteTimestamp);
    }

    public static String stableProcessingKey(
            String serviceName,
            String username,
            String address,
            long voteTimestamp
    ) {
        String source = String.join(
                "\u0000",
                value(serviceName),
                value(username),
                value(address),
                Long.toString(voteTimestamp)
        );
        return "vote." + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
