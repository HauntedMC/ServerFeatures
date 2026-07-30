package nl.hauntedmc.serverfeatures.features.playercount.internal;

import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe latest-value store with schema validation, ordering and expiry.
 */
public final class PlayerCountSnapshotStore {

    public enum ApplyResult {
        APPLIED,
        STALE,
        INVALID
    }

    private final String localServerName;
    private final long staleAfterMillis;
    private final String expectedPublisherId;
    private final AtomicReference<PlayerCountSnapshot> current = new AtomicReference<>();
    private final Set<String> retiredPublisherEpochs = new HashSet<>();

    public PlayerCountSnapshotStore(
            String localServerName,
            long staleAfterMillis,
            String expectedPublisherId
    ) {
        this.localServerName = requireServerName(localServerName);
        if (staleAfterMillis <= 0L) {
            throw new IllegalArgumentException("staleAfterMillis must be positive");
        }
        this.staleAfterMillis = staleAfterMillis;
        this.expectedPublisherId = requireText(expectedPublisherId, "expectedPublisherId");
    }

    public synchronized ApplyResult apply(
            PlayerCountSnapshotMessage message,
            long receivedAtEpochMillis
    ) {
        PlayerCountSnapshot candidate = validate(message, receivedAtEpochMillis);
        if (candidate == null) {
            return ApplyResult.INVALID;
        }
        PlayerCountSnapshot existing = current.get();
        if (retiredPublisherEpochs.contains(candidate.publisherEpoch())) {
            return ApplyResult.STALE;
        }
        if (existing != null && !isNewer(candidate, existing)) {
            return ApplyResult.STALE;
        }
        if (existing != null && !candidate.publisherEpoch().equals(existing.publisherEpoch())) {
            retiredPublisherEpochs.add(existing.publisherEpoch());
        }
        current.set(candidate);
        return ApplyResult.APPLIED;
    }

    public Optional<PlayerCountSnapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public Optional<PlayerCountSnapshot> currentFresh(long nowEpochMillis) {
        PlayerCountSnapshot snapshot = current.get();
        if (snapshot == null || isStale(snapshot, nowEpochMillis)) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    public Optional<PlayerCountSnapshot.Counts> network(long nowEpochMillis) {
        return currentFresh(nowEpochMillis).map(PlayerCountSnapshot::network);
    }

    public Optional<PlayerCountSnapshot.Counts> localServer(long nowEpochMillis) {
        return server(localServerName, nowEpochMillis);
    }

    public Optional<PlayerCountSnapshot.Counts> server(String serverName, long nowEpochMillis) {
        String normalized = PlayerCountSnapshotMessage.normalizeServerName(serverName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return currentFresh(nowEpochMillis).map(snapshot -> snapshot.server(normalized));
    }

    public boolean isAvailable(long nowEpochMillis) {
        return currentFresh(nowEpochMillis).isPresent();
    }

    public boolean isStale(long nowEpochMillis) {
        PlayerCountSnapshot snapshot = current.get();
        return snapshot != null && isStale(snapshot, nowEpochMillis);
    }

    public long ageMillis(long nowEpochMillis) {
        PlayerCountSnapshot snapshot = current.get();
        if (snapshot == null) {
            return -1L;
        }
        return Math.max(0L, nowEpochMillis - snapshot.receivedAtEpochMillis());
    }

    public synchronized void clear() {
        current.set(null);
        retiredPublisherEpochs.clear();
    }

    public String getLocalServerName() {
        return localServerName;
    }

    public String getExpectedPublisherId() {
        return expectedPublisherId;
    }

    private boolean isStale(PlayerCountSnapshot snapshot, long nowEpochMillis) {
        return nowEpochMillis - snapshot.receivedAtEpochMillis() > staleAfterMillis;
    }

    private static boolean isNewer(PlayerCountSnapshot candidate, PlayerCountSnapshot existing) {
        if (candidate.publisherEpoch().equals(existing.publisherEpoch())) {
            return candidate.sequence() > existing.sequence();
        }
        return candidate.publishedAtEpochMillis() > existing.publishedAtEpochMillis();
    }

    private PlayerCountSnapshot validate(
            PlayerCountSnapshotMessage message,
            long receivedAtEpochMillis
    ) {
        if (message == null
                || message.getSchemaVersion() != PlayerCountSnapshotMessage.SCHEMA_VERSION
                || blank(message.getPublisherId())
                || !expectedPublisherId.equals(message.getPublisherId().trim())
                || blank(message.getPublisherEpoch())
                || message.getSequence() <= 0L
                || message.getPublishedAtEpochMillis() <= 0L
                || receivedAtEpochMillis <= 0L
                || !validCounts(message.getNetworkOnline(), message.getNetworkVanished())) {
            return null;
        }

        Map<String, PlayerCountSnapshot.Counts> servers = new LinkedHashMap<>();
        long serverOnlineTotal = 0L;
        long serverVanishedTotal = 0L;
        Map<String, PlayerCountSnapshotMessage.ServerCounts> incoming = message.getServers();
        for (Map.Entry<String, PlayerCountSnapshotMessage.ServerCounts> entry : incoming.entrySet()) {
            String serverName = PlayerCountSnapshotMessage.normalizeServerName(entry.getKey());
            PlayerCountSnapshotMessage.ServerCounts counts = entry.getValue();
            if (serverName.isEmpty()
                    || counts == null
                    || !validCounts(counts.getOnline(), counts.getVanished())
                    || servers.containsKey(serverName)) {
                return null;
            }
            servers.put(
                    serverName,
                    new PlayerCountSnapshot.Counts(counts.getOnline(), counts.getVanished())
            );
            serverOnlineTotal += counts.getOnline();
            serverVanishedTotal += counts.getVanished();
        }
        if (serverOnlineTotal > message.getNetworkOnline()
                || serverVanishedTotal > message.getNetworkVanished()) {
            return null;
        }

        return new PlayerCountSnapshot(
                message.getPublisherId().trim(),
                message.getPublisherEpoch().trim(),
                message.getSequence(),
                message.getPublishedAtEpochMillis(),
                receivedAtEpochMillis,
                new PlayerCountSnapshot.Counts(
                        message.getNetworkOnline(),
                        message.getNetworkVanished()
                ),
                servers
        );
    }

    private static boolean validCounts(int online, int vanished) {
        return online >= 0 && vanished >= 0 && vanished <= online;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String requireServerName(String value) {
        String normalized = PlayerCountSnapshotMessage.normalizeServerName(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("localServerName must not be blank");
        }
        return normalized;
    }
}
