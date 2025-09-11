package nl.hauntedmc.serverfeatures.features.votifier.event;

/**
 * Mirrors the shape of Votifier's Vote for easy migration.
 */
public final class VotePayload {
    private final String serviceName;
    private final String username;
    private final String address;
    private final long   voteTimestamp;

    public VotePayload(String serviceName, String username, String address, long voteTimestamp) {
        this.serviceName = serviceName;
        this.username = username;
        this.address = address == null ? "-" : address;
        this.voteTimestamp = voteTimestamp;
    }

    public String getServiceName() { return serviceName; }
    public String getUsername()    { return username; }
    public String getAddress()     { return address; }
    public String getTimeStamp()   { return String.valueOf(voteTimestamp); }
    public long getVoteTimestamp() { return voteTimestamp; }
}
