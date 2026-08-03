package nl.hauntedmc.serverfeatures.features.votereward.internal;

public record IncomingVote(
        String serviceName,
        String username,
        String address,
        long timestamp,
        String processingKey
) {
    public IncomingVote {
        if (processingKey == null || processingKey.isBlank()) {
            throw new IllegalArgumentException("processingKey must not be blank");
        }
        processingKey = processingKey.trim();
    }
}
