package nl.hauntedmc.serverfeatures.features.votifier.event;

/**
 * Mirrors the shape of Votifier's Vote for easy migration.
 */
public record VotePayload(String serviceName, String username, String address, long voteTimestamp) {
    public VotePayload(String serviceName, String username, String address, long voteTimestamp) {
        this.serviceName = serviceName;
        this.username = username;
        this.address = address == null ? "-" : address;
        this.voteTimestamp = voteTimestamp;
    }

    public String getTimeStamp() {
        return String.valueOf(voteTimestamp);
    }
}
