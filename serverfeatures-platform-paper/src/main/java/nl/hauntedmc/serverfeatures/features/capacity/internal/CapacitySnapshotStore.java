package nl.hauntedmc.serverfeatures.features.capacity.internal;

import nl.hauntedmc.serverfeatures.features.capacity.messaging.CapacitySnapshotMessage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe latest-value store with schema validation, ordering and expiry. */
public final class CapacitySnapshotStore {

    private static final int MAX_RETIRED_PUBLISHER_EPOCHS = 32;

    public enum ApplyResult {
        APPLIED,
        STALE,
        INVALID
    }

    private final String localServerName;
    private final long staleAfterMillis;
    private final String expectedPublisherId;
    private final AtomicReference<CapacitySnapshot> current = new AtomicReference<>();
    private final AtomicReference<CapacitySnapshot> invalidated = new AtomicReference<>();
    private final Set<String> retiredPublisherEpochs = new LinkedHashSet<>();

    public CapacitySnapshotStore(
            String localServerName,
            long staleAfterMillis,
            String expectedPublisherId
    ) {
        this.localServerName = requireScopeName(localServerName, "localServerName");
        if (staleAfterMillis <= 0L) {
            throw new IllegalArgumentException("staleAfterMillis must be positive");
        }
        this.staleAfterMillis = staleAfterMillis;
        this.expectedPublisherId = requireText(expectedPublisherId, "expectedPublisherId");
    }

    public synchronized ApplyResult apply(
            CapacitySnapshotMessage message,
            long receivedAtEpochMillis
    ) {
        CapacitySnapshot candidate = validate(message, receivedAtEpochMillis);
        if (candidate == null) {
            return ApplyResult.INVALID;
        }

        CapacitySnapshot existing = current.get();
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

    public Optional<CapacitySnapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public Optional<CapacitySnapshot> currentFresh(long nowEpochMillis) {
        CapacitySnapshot snapshot = current.get();
        if (snapshot == null) {
            return Optional.empty();
        }
        if (isStaleSnapshot(snapshot, nowEpochMillis)) {
            invalidate(snapshot);
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    public Optional<CapacitySnapshot.Scope> proxy(long nowEpochMillis) {
        return currentFresh(nowEpochMillis).map(CapacitySnapshot::proxy);
    }

    public Optional<CapacitySnapshot.Scope> gameplay(long nowEpochMillis) {
        return currentFresh(nowEpochMillis).map(CapacitySnapshot::gameplay);
    }

    public Optional<CapacitySnapshot.Scope> group(String groupName, long nowEpochMillis) {
        String normalized = CapacitySnapshotMessage.normalizeScopeName(groupName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return currentFresh(nowEpochMillis)
                .flatMap(snapshot -> snapshot.findGroup(normalized));
    }

    public Optional<CapacitySnapshot.Scope> localServer(long nowEpochMillis) {
        return server(localServerName, nowEpochMillis);
    }

    public Optional<CapacitySnapshot.Scope> server(String serverName, long nowEpochMillis) {
        String normalized = CapacitySnapshotMessage.normalizeScopeName(serverName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return currentFresh(nowEpochMillis)
                .flatMap(snapshot -> snapshot.findServer(normalized));
    }

    public boolean isAvailable(long nowEpochMillis) {
        return currentFresh(nowEpochMillis).isPresent();
    }

    public boolean isGroupAvailable(String groupName, long nowEpochMillis) {
        return group(groupName, nowEpochMillis).isPresent();
    }

    public boolean isLocalServerAvailable(long nowEpochMillis) {
        return localServer(nowEpochMillis).isPresent();
    }

    public boolean isServerAvailable(String serverName, long nowEpochMillis) {
        return server(serverName, nowEpochMillis).isPresent();
    }

    public boolean isStale(long nowEpochMillis) {
        CapacitySnapshot snapshot = current.get();
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
        CapacitySnapshot snapshot = current.get();
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

    private boolean isStaleSnapshot(CapacitySnapshot snapshot, long nowEpochMillis) {
        if (invalidated.get() == snapshot) {
            return true;
        }
        long receivedAt = snapshot.receivedAtEpochMillis();
        return nowEpochMillis < receivedAt || nowEpochMillis - receivedAt > staleAfterMillis;
    }

    private void invalidate(CapacitySnapshot snapshot) {
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

    private CapacitySnapshot validate(
            CapacitySnapshotMessage message,
            long receivedAtEpochMillis
    ) {
        if (message == null
                || message.getSchemaVersion() != CapacitySnapshotMessage.SCHEMA_VERSION
                || blank(message.getPublisherId())
                || !expectedPublisherId.equals(message.getPublisherId().trim())
                || blank(message.getPublisherEpoch())
                || message.getSequence() <= 0L
                || message.getPublishedAtEpochMillis() <= 0L
                || receivedAtEpochMillis <= 0L
                || message.getActiveLeases() < 0) {
            return null;
        }

        CapacitySnapshot.Scope proxy = validateScope(message.getProxy(), "proxy");
        CapacitySnapshot.Scope gameplay = validateScope(message.getGameplay(), "gameplay");
        if (proxy == null || gameplay == null) {
            return null;
        }

        Map<String, CapacitySnapshot.Scope> groups = validateScopes(message.getGroups());
        Map<String, CapacitySnapshot.Scope> servers = validateScopes(message.getServers());
        if (groups == null || servers == null) {
            return null;
        }

        return new CapacitySnapshot(
                message.getPublisherId().trim(),
                message.getPublisherEpoch().trim(),
                message.getSequence(),
                message.getPublishedAtEpochMillis(),
                receivedAtEpochMillis,
                message.getActiveLeases(),
                proxy,
                gameplay,
                groups,
                servers
        );
    }

    private static Map<String, CapacitySnapshot.Scope> validateScopes(
            Map<String, CapacitySnapshotMessage.Scope> incoming
    ) {
        Map<String, CapacitySnapshot.Scope> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, CapacitySnapshotMessage.Scope> entry : incoming.entrySet()) {
            String scopeName = CapacitySnapshotMessage.normalizeScopeName(entry.getKey());
            CapacitySnapshot.Scope scope = validateScope(entry.getValue(), scopeName);
            if (scopeName.isEmpty()
                    || scope == null
                    || !scopeName.equals(scope.name())
                    || normalized.putIfAbsent(scopeName, scope) != null) {
                return null;
            }
        }
        return Map.copyOf(normalized);
    }

    private static CapacitySnapshot.Scope validateScope(
            CapacitySnapshotMessage.Scope incoming,
            String expectedName
    ) {
        if (incoming == null
                || incoming.getCapacity() < 0
                || incoming.getReservedSlots() < 0
                || incoming.getReservedSlots() > incoming.getCapacity()
                || incoming.getOccupied() < 0
                || incoming.getPending() < 0
                || incoming.getRestorationReserved() < 0
                || blank(incoming.getState())) {
            return null;
        }

        String name = CapacitySnapshotMessage.normalizeScopeName(incoming.getName());
        String normalizedExpected = CapacitySnapshotMessage.normalizeScopeName(expectedName);
        if (name.isEmpty() || !name.equals(normalizedExpected)) {
            return null;
        }

        CapacitySnapshot.State state;
        try {
            state = CapacitySnapshot.State.valueOf(
                    incoming.getState().trim().toUpperCase(java.util.Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }

        return new CapacitySnapshot.Scope(
                name,
                incoming.getCapacity(),
                incoming.getReservedSlots(),
                incoming.getOccupied(),
                incoming.getPending(),
                incoming.getRestorationReserved(),
                state
        );
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

    private static String requireScopeName(String value, String fieldName) {
        String normalized = CapacitySnapshotMessage.normalizeScopeName(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
