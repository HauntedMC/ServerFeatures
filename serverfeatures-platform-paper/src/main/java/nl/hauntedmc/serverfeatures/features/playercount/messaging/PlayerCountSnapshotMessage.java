package nl.hauntedmc.serverfeatures.features.playercount.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

import java.util.Locale;
import java.util.Map;

/**
 * Backend decoder for the versioned PlayerCount snapshot wire contract.
 *
 * <p>The authoritative producer contract lives in ProxyFeatures contracts. This local decoder
 * deliberately depends only on the stable JSON wire shape, allowing both repositories to be
 * reviewed and released independently.</p>
 */
public final class PlayerCountSnapshotMessage extends AbstractEventMessage {

    public static final String TYPE = "playercount_snapshot";
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion;
    private String publisherId;
    private String publisherEpoch;
    private long sequence;
    private long publishedAtEpochMillis;
    private int networkOnline;
    private int networkVanished;
    private Map<String, ServerCounts> servers;

    @SuppressWarnings("unused")
    private PlayerCountSnapshotMessage() {
        super(TYPE);
        this.schemaVersion = SCHEMA_VERSION;
        this.servers = Map.of();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public String getPublisherEpoch() {
        return publisherEpoch;
    }

    public long getSequence() {
        return sequence;
    }

    public long getPublishedAtEpochMillis() {
        return publishedAtEpochMillis;
    }

    public int getNetworkOnline() {
        return networkOnline;
    }

    public int getNetworkVanished() {
        return networkVanished;
    }

    public Map<String, ServerCounts> getServers() {
        return servers == null || servers.isEmpty() ? Map.of() : Map.copyOf(servers);
    }

    public static String normalizeServerName(String serverName) {
        if (serverName == null) {
            return "";
        }
        return serverName.trim().toLowerCase(Locale.ROOT);
    }

    public static final class ServerCounts {
        private int online;
        private int vanished;

        @SuppressWarnings("unused")
        private ServerCounts() {
        }

        public int getOnline() {
            return online;
        }

        public int getVanished() {
            return vanished;
        }
    }
}
