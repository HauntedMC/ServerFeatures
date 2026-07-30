package nl.hauntedmc.serverfeatures.features.playercount.internal;

import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;

import java.util.Map;

/**
 * Validated immutable snapshot cached on a backend server.
 */
public record PlayerCountSnapshot(
        String publisherId,
        String publisherEpoch,
        long sequence,
        long publishedAtEpochMillis,
        long receivedAtEpochMillis,
        Counts network,
        Map<String, Counts> servers
) {

    public PlayerCountSnapshot {
        servers = servers == null || servers.isEmpty() ? Map.of() : Map.copyOf(servers);
    }

    public Counts server(String serverName) {
        return servers.getOrDefault(
                PlayerCountSnapshotMessage.normalizeServerName(serverName),
                Counts.empty()
        );
    }

    public record Counts(int online, int vanished) {
        public Counts {
            if (online < 0 || vanished < 0 || vanished > online) {
                throw new IllegalArgumentException("invalid player counts");
            }
        }

        public static Counts empty() {
            return new Counts(0, 0);
        }

        public int visible() {
            return Math.max(0, online - vanished);
        }
    }
}
