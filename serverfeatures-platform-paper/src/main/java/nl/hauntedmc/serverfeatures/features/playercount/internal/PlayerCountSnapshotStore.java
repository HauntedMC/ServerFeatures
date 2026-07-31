package nl.hauntedmc.serverfeatures.features.playercount.internal;

import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe latest-value store with schema validation, ordering and expiry.
 */
public final class PlayerCountSnapshotStore {

    private static final int MAX_RETIRED_PUBLISHER_EPOCHS = 32;

    public enum ApplyResult {
        APPLIED,
        STALE,
        INVALID
    }

    private final String localServerName;
    private final long staleAfterMillis;
    private final String expectedPublisherId;
    private final AtomicReference<PlayerCountSnapshot> current = new AtomicReference<>();
    private final AtomicReference<PlayerCountSnapshot> invalidated = new AtomicReference<>();
    private final Set<String> retiredPublisherEpochs = new LinkedHashSet<>();

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
        if (existing != null) {
            boolean sameEpoch = candidate.publisherEpoch().equals(existing.publisherEpoch());
            if (sameEpoch && candidate.sequence() <= existing.sequence()) {
                return ApplyResult.STALE;
            }
            if (!sameEpoch
                    && candidate.publishedAtEpochMillis() <= existing.publishedAtEpochMillis()
                    && !isStaleSnapshot(existing, receivedAtEpochMillis)) {
                return ApplyResult.STALE;
            }
            if (!sameEpoch) {
                retireEpoch(existing.publisherEpoch());
            }
        }
        current.set(candidate);
        invalidated.set(null);
        return ApplyResult.APPLIED;
    }

    public Optional<PlayerCountSnapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public Optional<PlayerCountSnapshot> currentFresh(long nowEpochMillis) {
        PlayerCountSnapshot snapshot = current.get();
        if (snapshot == null) {
            return Optional.empty();
        }
        if (isStaleSnapshot(snapshot, nowEpochMillis)) {
            invalidate(snapshot);
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
        return currentFresh(nowEpochMillis)
                .flatMap(snapshot -> snapshot.findServer(normalized));
    }

    public boolean isAvailable(long nowEpochMillis) {
        return currentFresh(nowEpochMillis).isPresent();
    }

    public boolean isLocalServerAvailable(long nowEpochMillis) {
        return localServer(nowEpochMillis).isPresent();
    }

    public boolean isServerAvailable(String serverName, long nowEpochMillis) {
        return server(serverName, nowEpochMillis).isPresent();
    }

    public boolean isStale(long nowEpochMillis) {
        PlayerCountSnapshot snapshot = current.get();
        if (snapshot == null) {
            return false;
        }
        if (isStaleSnapshot(snapshot, nowEpochMillis)) {
            invalidate(snapshot);
            return true;
        }
        return false;
    }

    public long ageMillis(long nowEpochMillis) {
        PlayerCountSnapshot snapshot = current.get();
        if (snapshot == null || nowEpochMillis < snapshot.receivedAtEpochMillis()) {
            return -1L;
        }
        return nowEpochMillis - snapshot.receivedAtEpochMillis();
    }

    public synchronized void clear() {
        current.set(null);
        invalidated.set(null);
        retiredPublisherEpochs.clear();
    }

    public String getLocalServerName() {
        return localServerName;
    }

    public String getExpectedPublisherId() {
        return expectedPublisherId;
    }

    private boolean isStaleSnapshot(PlayerCountSnapshot snapshot, long nowEpochMillis) {
        if (invalidated.get() == snapshot) {
            return true;
        }
        long receivedAt = snapshot.receivedAtEpochMillis();
        return nowEpochMillis < receivedAt || nowEpochMillis - receivedAt > staleAfterMillis;
    }

    private void invalidate(PlayerCountSnapshot snapshot) {
        if (current.get() == snapshot) {
            invalidated.set(snapshot);
        }
    }

    private void retireEpoch(String publisherEpoch) {
        retiredPublisherEpochs.add(publisherEpoch);
        while (retiredPublisherEpochs.size() > MAX_RETIRED_PUBLISHER_EPOCHS) {
            Iterator<String> iterator = retiredPublisherEpochs.iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
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
