package nl.hauntedmc.serverfeatures.features.economy.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.persistence.LockModeType;
import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowEvent;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowState;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyTransactionEntity;
import nl.hauntedmc.serverfeatures.features.economy.entity.EconomyWorkflowEntity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import org.hibernate.Session;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persists, leases, and acknowledges at-least-once Economy fulfilment events. */
final class EconomyWorkflowStore {
    private static final Gson GSON = new Gson();
    private static final Type METADATA_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final int MAX_ATTEMPTS = 8;
    private static final long LEASE_MILLIS = 30_000L;
    private static final long MAX_RETRY_MILLIS = 60_000L;

    WorkflowSnapshot enqueue(Session session, EconomyTransactionEntity transaction, EconomyWorkflowRequest request,
                             Identity identity, EconomySettings.Currency currency, BigDecimal amount,
                             String fingerprint, long now) {
        EconomyWorkflowEntity entity = new EconomyWorkflowEntity();
        entity.setEventId(UUID.randomUUID().toString());
        entity.setSource(request.workflow().source());
        entity.setWorkflowKey(request.workflow().workflowId());
        entity.setRequestFingerprint(fingerprint);
        entity.setOperationId(transaction.getOperationId());
        entity.setPlayerId(identity.playerId());
        entity.setPlayerUuid(identity.playerUuid().toString());
        entity.setPlayerName(EconomyPersistenceValues.trim(identity.playerName(), 32));
        entity.setCurrencyId(currency.id());
        entity.setScopeKey(currency.scope().key());
        entity.setAmount(EconomyPersistenceValues.databaseAmount(amount));
        entity.setEventType(request.eventType());
        String metadata = GSON.toJson(request.metadata());
        if (metadata.length() > 4096) {
            throw new IllegalArgumentException("Economy workflow metadata exceeds 4096 serialized characters");
        }
        entity.setMetadataJson(metadata);
        entity.setState(State.PENDING.name());
        entity.setAttempts(0);
        entity.setAvailableAt(now);
        entity.setLastError("");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        session.persist(entity);
        return snapshot(entity);
    }

    WorkflowSnapshot require(Session session, EconomyWorkflowRef reference, String fingerprint) {
        EconomyWorkflowEntity entity = findEntity(session, reference).orElseThrow(() -> new IllegalStateException(
                "Economy workflow is missing for committed operation " + reference.source() + "/" + reference.workflowId()
        ));
        if (!Objects.equals(entity.getRequestFingerprint(), fingerprint)) {
            throw new IllegalStateException("Economy workflow fingerprint differs from its committed transaction");
        }
        return snapshot(entity);
    }

    Optional<WorkflowSnapshot> find(Session session, EconomyWorkflowRef reference) {
        return findEntity(session, reference).map(EconomyWorkflowStore::snapshot);
    }

    List<Claim> claim(Session session, Set<String> eventTypes, String owner, long now, int limit) {
        if (eventTypes.isEmpty()) {
            return List.of();
        }
        List<EconomyWorkflowEntity> candidates = session.createSelectionQuery(
                        "from EconomyWorkflowEntity where eventType in :eventTypes and ((state = :pending and availableAt <= :now) "
                                + "or (state = :dispatching and leaseExpiresAt <= :now)) order by createdAt asc",
                        EconomyWorkflowEntity.class)
                .setParameter("eventTypes", eventTypes)
                .setParameter("pending", State.PENDING.name())
                .setParameter("dispatching", State.DISPATCHING.name())
                .setParameter("now", now)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        for (EconomyWorkflowEntity entity : candidates) {
            // A server may die while a handler is still running, leaving its lease to expire.
            // Unlike an explicit handler failure, that path never calls release(), so enforce
            // the same attempt ceiling before leasing the event again.
            if (entity.getAttempts() >= MAX_ATTEMPTS) {
                entity.setState(State.DEAD_LETTER.name());
                entity.setLeaseOwner(null);
                entity.setLeaseExpiresAt(null);
                if (entity.getLastError() == null || entity.getLastError().isBlank()) {
                    entity.setLastError("Workflow lease expired after maximum delivery attempts");
                }
                entity.setAvailableAt(Long.MAX_VALUE);
                entity.setUpdatedAt(now);
                continue;
            }
            entity.setState(State.DISPATCHING.name());
            entity.setLeaseOwner(owner);
            entity.setLeaseExpiresAt(Math.addExact(now, LEASE_MILLIS));
            entity.setAttempts(Math.addExact(entity.getAttempts(), 1));
            entity.setUpdatedAt(now);
        }
        return candidates.stream()
                .filter(entity -> State.DISPATCHING.name().equals(entity.getState()) && owner.equals(entity.getLeaseOwner()))
                .map(entity -> new Claim(entity.getEventId(), owner, event(entity))).toList();
    }

    void acknowledge(Session session, String eventId, String owner, long now) {
        EconomyWorkflowEntity entity = session.find(EconomyWorkflowEntity.class, eventId, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null || !State.DISPATCHING.name().equals(entity.getState())
                || !owner.equals(entity.getLeaseOwner())) {
            return;
        }
        entity.setState(State.DELIVERED.name());
        entity.setLeaseOwner(null);
        entity.setLeaseExpiresAt(null);
        entity.setLastError("");
        entity.setDeliveredAt(now);
        entity.setUpdatedAt(now);
    }

    void release(Session session, String eventId, String owner, String failure, long now) {
        EconomyWorkflowEntity entity = session.find(EconomyWorkflowEntity.class, eventId, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null || !State.DISPATCHING.name().equals(entity.getState())
                || !owner.equals(entity.getLeaseOwner())) {
            return;
        }
        boolean exhausted = entity.getAttempts() >= MAX_ATTEMPTS;
        entity.setState(exhausted ? State.DEAD_LETTER.name() : State.PENDING.name());
        entity.setLeaseOwner(null);
        entity.setLeaseExpiresAt(null);
        entity.setLastError(EconomyPersistenceValues.trim(failure, 512));
        entity.setAvailableAt(exhausted ? Long.MAX_VALUE : Math.addExact(now, retryDelay(entity.getAttempts())));
        entity.setUpdatedAt(now);
    }

    private static Optional<EconomyWorkflowEntity> findEntity(Session session, EconomyWorkflowRef reference) {
        return session.createSelectionQuery(
                        "from EconomyWorkflowEntity where source = :source and workflowKey = :workflowKey",
                        EconomyWorkflowEntity.class)
                .setParameter("source", reference.source())
                .setParameter("workflowKey", reference.workflowId())
                .setMaxResults(1)
                .getResultStream().findFirst();
    }

    private static WorkflowSnapshot snapshot(EconomyWorkflowEntity entity) {
        return new WorkflowSnapshot(UUID.fromString(entity.getEventId()), UUID.fromString(entity.getOperationId()),
                state(entity.getState()), entity.getAttempts(), entity.getLastError());
    }

    private static EconomyWorkflowEvent event(EconomyWorkflowEntity entity) {
        Map<String, String> metadata = GSON.fromJson(entity.getMetadataJson(), METADATA_TYPE);
        return new EconomyWorkflowEvent(UUID.fromString(entity.getEventId()),
                new EconomyWorkflowRef(entity.getSource(), entity.getWorkflowKey()),
                UUID.fromString(entity.getOperationId()),
                new EconomyAccountRef(entity.getPlayerId(), UUID.fromString(entity.getPlayerUuid()), entity.getPlayerName(),
                        entity.getCurrencyId(), entity.getScopeKey()),
                entity.getAmount(), entity.getEventType(), metadata == null ? Map.of() : metadata, entity.getCreatedAt());
    }

    private static EconomyWorkflowState state(String state) {
        return switch (State.valueOf(state)) {
            case PENDING, DISPATCHING -> EconomyWorkflowState.PENDING_FULFILMENT;
            case DELIVERED -> EconomyWorkflowState.DELIVERED;
            case DEAD_LETTER -> EconomyWorkflowState.DEAD_LETTER;
        };
    }

    private static long retryDelay(int attempts) {
        int shift = Math.min(6, Math.max(0, attempts - 1));
        return Math.min(MAX_RETRY_MILLIS, 1_000L << shift);
    }

    record WorkflowSnapshot(UUID eventId, UUID operationId, EconomyWorkflowState state, int attempts, String lastError) { }
    record Claim(String eventId, String owner, EconomyWorkflowEvent event) { }

    private enum State {
        PENDING,
        DISPATCHING,
        DELIVERED,
        DEAD_LETTER
    }
}
