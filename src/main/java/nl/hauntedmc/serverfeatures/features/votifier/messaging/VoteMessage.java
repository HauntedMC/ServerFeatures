package nl.hauntedmc.serverfeatures.features.votifier.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

public final class VoteMessage extends AbstractEventMessage {
    private final String serviceName;
    private final String username;
    private final String address;
    private final long voteTimestamp;

    // No-arg voor Gson
    @SuppressWarnings("unused")
    private VoteMessage() {
        super("votifier");
        this.serviceName = null;
        this.username = null;
        this.address = null;
        this.voteTimestamp = 0L;
    }

    public VoteMessage(String serviceName, String username, String address, long voteTimestamp) {
        super("votifier");
        this.serviceName = serviceName;
        this.username = username;
        this.address = address;
        this.voteTimestamp = voteTimestamp;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getUsername() {
        return username;
    }

    public String getAddress() {
        return address;
    }

    public long getVoteTimestamp() {
        return voteTimestamp;
    }
}
