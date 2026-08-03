package nl.hauntedmc.serverfeatures.features.votifier.event;

public record VotePayload(
        String serviceName,
        String username,
        String address,
        long voteTimestamp,
        String processingKey
) {
    public VotePayload {
        if (processingKey == null || processingKey.isBlank()) {
            throw new IllegalArgumentException("processingKey must not be blank");
        }
        processingKey = processingKey.trim();
        address = address == null ? "-" : address;
    }
}
