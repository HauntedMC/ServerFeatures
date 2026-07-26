package nl.hauntedmc.serverfeatures.features.votereward.internal;

import nl.hauntedmc.serverfeatures.features.votifier.event.VotePayload;

public record IncomingVote(
        String serviceName,
        String username,
        String address,
        long timestamp,
        String processingKey
) {
    public IncomingVote(String serviceName, String username, String address, long timestamp) {
        this(
                serviceName,
                username,
                address,
                timestamp,
                VotePayload.stableProcessingKey(serviceName, username, address, timestamp)
        );
    }
}
