package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import jakarta.persistence.LockModeType;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous persistence boundary for Graveyard. No caller should invoke ORM work on the Paper
 * thread; every public method schedules its transaction through the feature task manager.
 */
public final class GraveRepository {
    private static final Set<GraveStatus> LOADABLE_STATES = EnumSet.of(
            GraveStatus.ACTIVE,
            GraveStatus.PARTIAL,
            GraveStatus.ORPHANED_WORLD,
            GraveStatus.DELIVERY_PENDING,
            GraveStatus.CORRUPT
    );

    private final Graveyard feature;
    private final ORMContext orm;
    private final PlayerIdentityResolver playerIdentityResolver;

    public GraveRepository(Graveyard feature, ORMContext orm) {
        this.feature = feature;
        this.orm = orm;
        this.playerIdentityResolver = new PlayerIdentityResolver(
                feature.getPlugin().getDataRegistry()
                        .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Graveyard."))
        );
    }

    public CompletionStage<List<Grave>> loadRuntimeGraves(String serverId, String inventoryScope) {
        return async(() -> orm.runInTransaction(session -> session.createSelectionQuery(
                        "FROM GraveMetadataEntity g "
                                + "WHERE g.serverId = :serverId "
                                + "AND g.inventoryScope = :scope "
                                + "AND g.state IN :states",
                        GraveMetadataEntity.class
                )
                .setParameter("serverId", serverId)
                .setParameter("scope", inventoryScope)
                .setParameter("states", LOADABLE_STATES.stream().map(Enum::name).toList())
                .getResultList()
                .stream()
                .map(this::toGrave)
                .toList()));
    }

    public CompletionStage<Optional<EncodedGravePayload>> loadPayload(UUID graveId) {
        return async(() -> orm.runInTransaction(session -> {
            GravePayloadEntity entity = session.find(GravePayloadEntity.class, graveId.toString());
            if (entity == null) {
                return Optional.empty();
            }
            return Optional.of(new EncodedGravePayload(entity.getPayload(), entity.getPayloadChecksum()));
        }));
    }

    public CompletionStage<Void> saveCaptured(Grave grave, EncodedGravePayload payload) {
        CompletionStage<Optional<Long>> playerId = playerIdentityResolver.findByUuid(grave.ownerUuid())
                .thenApply(identity -> identity.map(value -> value.playerId()));
        return playerId.thenCompose(resolvedPlayerId -> async(() -> {
            orm.runInTransaction(session -> {
                GraveMetadataEntity metadata = toEntity(grave, resolvedPlayerId.orElse(null));
                GravePayloadEntity payloadEntity = toPayloadEntity(grave.graveId(), grave.payloadRevision(), payload);
                session.merge(metadata);
                session.merge(payloadEntity);
                persistAudit(
                        session,
                        grave,
                        null,
                        "CAPTURED",
                        null,
                        null,
                        grave.status(),
                        0,
                        grave.itemEntryCount(),
                        0,
                        grave.remainingExperience(),
                        null
                );
                return null;
            });
            return null;
        }));
    }

    public CompletionStage<Boolean> reserveOperation(
            UUID graveId,
            UUID operationToken,
            Set<GraveStatus> allowedStates
    ) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    graveId.toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null || metadata.getOperationToken() != null) {
                return false;
            }
            GraveStatus current = GraveStatus.valueOf(metadata.getState());
            if (!allowedStates.contains(current)) {
                return false;
            }
            metadata.setOperationToken(operationToken.toString());
            metadata.setOperationStartedMillis(System.currentTimeMillis());
            metadata.setUpdatedAt(System.currentTimeMillis());
            return true;
        }));
    }

    public CompletionStage<Boolean> releaseOperation(UUID graveId, UUID operationToken) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    graveId.toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (!ownsOperation(metadata, operationToken)) {
                return false;
            }
            metadata.setOperationToken(null);
            metadata.setOperationStartedMillis(null);
            metadata.setUpdatedAt(System.currentTimeMillis());
            return true;
        }));
    }

    public CompletionStage<Boolean> finalizeClaim(
            Grave grave,
            UUID operationToken,
            UUID actorUuid,
            GravePayload previous,
            GravePayload remaining,
            EncodedGravePayload encodedRemaining,
            GraveStatus finalStatus,
            int transferredEntries,
            int transferredExperience
    ) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (!ownsOperation(metadata, operationToken)
                    || metadata.getPayloadRevision() != previous.revision()) {
                return false;
            }

            GravePayloadEntity payloadEntity = session.find(
                    GravePayloadEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (payloadEntity == null || payloadEntity.getPayloadRevision() != previous.revision()) {
                return false;
            }

            payloadEntity.setPayloadRevision(remaining.revision());
            payloadEntity.setPayloadCodec(GravePayloadCodec.CODEC_VERSION);
            payloadEntity.setCompressed(false);
            payloadEntity.setPayload(encodedRemaining.bytes());
            payloadEntity.setPayloadChecksum(encodedRemaining.checksum());
            payloadEntity.setUpdatedAt(System.currentTimeMillis());

            GraveStatus oldStatus = GraveStatus.valueOf(metadata.getState());
            metadata.setState(finalStatus.name());
            metadata.setItemEntryCount(remaining.entries().size());
            metadata.setRemainingExperience(remaining.remainingExperience());
            metadata.setPayloadRevision(remaining.revision());
            metadata.setPayloadChecksum(encodedRemaining.checksum());
            metadata.setOperationToken(null);
            metadata.setOperationStartedMillis(null);
            metadata.setUpdatedAt(System.currentTimeMillis());
            if (finalStatus == GraveStatus.CLAIMED) {
                metadata.setCompletedAt(System.currentTimeMillis());
            }

            persistAudit(
                    session,
                    grave,
                    operationToken,
                    finalStatus == GraveStatus.CLAIMED ? "CLAIMED" : "PARTIAL_CLAIM",
                    actorUuid,
                    oldStatus,
                    finalStatus,
                    previous.entries().size(),
                    remaining.entries().size(),
                    previous.remainingExperience(),
                    remaining.remainingExperience(),
                    "transferredEntries=" + transferredEntries
                            + ", transferredExperience=" + transferredExperience
            );
            return true;
        }));
    }

    public CompletionStage<Boolean> restore(
            Grave grave,
            UUID actorUuid,
            long activeNow,
            long lifetimeMillis
    ) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null
                    || metadata.getOperationToken() != null
                    || GraveStatus.valueOf(metadata.getState()) != GraveStatus.EXPIRED) {
                return false;
            }
            long expiresAt = Math.addExact(activeNow, lifetimeMillis);
            metadata.setState(GraveStatus.ACTIVE.name());
            metadata.setExpiresActiveMillis(expiresAt);
            metadata.setPausedRemainingMillis(null);
            metadata.setCompletedAt(null);
            metadata.setUpdatedAt(System.currentTimeMillis());
            persistAudit(
                    session,
                    grave,
                    null,
                    "RESTORED",
                    actorUuid,
                    GraveStatus.EXPIRED,
                    GraveStatus.ACTIVE,
                    grave.itemEntryCount(),
                    grave.itemEntryCount(),
                    grave.remainingExperience(),
                    grave.remainingExperience(),
                    null
            );
            return true;
        }));
    }

    public CompletionStage<Boolean> transitionState(
            Grave grave,
            Set<GraveStatus> expectedStates,
            GraveStatus target,
            UUID actorUuid,
            String action,
            String details
    ) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null || metadata.getOperationToken() != null) {
                return false;
            }
            GraveStatus oldStatus = GraveStatus.valueOf(metadata.getState());
            if (!expectedStates.contains(oldStatus)) {
                return false;
            }
            metadata.setState(target.name());
            metadata.setUpdatedAt(System.currentTimeMillis());
            if (target == GraveStatus.EXPIRED
                    || target == GraveStatus.ADMIN_RECOVERED
                    || target == GraveStatus.PURGED) {
                metadata.setCompletedAt(System.currentTimeMillis());
            }
            persistAudit(
                    session,
                    grave,
                    null,
                    action,
                    actorUuid,
                    oldStatus,
                    target,
                    grave.itemEntryCount(),
                    grave.itemEntryCount(),
                    grave.remainingExperience(),
                    grave.remainingExperience(),
                    details
            );
            return true;
        }));
    }

    public CompletionStage<Boolean> purge(Grave grave, UUID actorUuid) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null || metadata.getOperationToken() != null) {
                return false;
            }
            GraveStatus oldStatus = GraveStatus.valueOf(metadata.getState());
            if (!EnumSet.of(
                    GraveStatus.EXPIRED,
                    GraveStatus.CORRUPT,
                    GraveStatus.ADMIN_RECOVERED
            ).contains(oldStatus)) {
                return false;
            }

            GravePayloadEntity payload = session.find(
                    GravePayloadEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (payload != null) {
                session.remove(payload);
            }

            int itemsBefore = metadata.getItemEntryCount();
            int experienceBefore = metadata.getRemainingExperience();
            long now = System.currentTimeMillis();
            metadata.setState(GraveStatus.PURGED.name());
            metadata.setItemEntryCount(0);
            metadata.setRemainingExperience(0);
            metadata.setPayloadRevision(Math.addExact(metadata.getPayloadRevision(), 1L));
            metadata.setPayloadChecksum("PURGED");
            metadata.setCompletedAt(now);
            metadata.setUpdatedAt(now);
            persistAudit(
                    session,
                    grave,
                    null,
                    "PURGED",
                    actorUuid,
                    oldStatus,
                    GraveStatus.PURGED,
                    itemsBefore,
                    0,
                    experienceBefore,
                    0,
                    "payloadDeleted=true"
            );
            return true;
        }));
    }

    public CompletionStage<Boolean> pauseForState(
            Grave grave,
            Set<GraveStatus> expectedStates,
            GraveStatus target,
            long remainingMillis,
            UUID actorUuid,
            String action
    ) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null || metadata.getOperationToken() != null) {
                return false;
            }
            GraveStatus oldStatus = GraveStatus.valueOf(metadata.getState());
            if (!expectedStates.contains(oldStatus)) {
                return false;
            }
            metadata.setState(target.name());
            metadata.setPausedRemainingMillis(Math.max(0L, remainingMillis));
            metadata.setUpdatedAt(System.currentTimeMillis());
            persistAudit(
                    session,
                    grave,
                    null,
                    action,
                    actorUuid,
                    oldStatus,
                    target,
                    grave.itemEntryCount(),
                    grave.itemEntryCount(),
                    grave.remainingExperience(),
                    grave.remainingExperience(),
                    "remainingActiveMillis=" + Math.max(0L, remainingMillis)
            );
            return true;
        }));
    }

    public CompletionStage<Boolean> resumeOrphaned(Grave grave, long activeNow) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null
                    || metadata.getOperationToken() != null
                    || GraveStatus.valueOf(metadata.getState()) != GraveStatus.ORPHANED_WORLD) {
                return false;
            }
            long remaining = metadata.getPausedRemainingMillis() == null
                    ? 0L
                    : Math.max(0L, metadata.getPausedRemainingMillis());
            GraveStatus target = metadata.getItemEntryCount() == 0 && metadata.getRemainingExperience() == 0
                    ? GraveStatus.CLAIMED
                    : GraveStatus.ACTIVE;
            metadata.setState(target.name());
            metadata.setExpiresActiveMillis(Math.addExact(activeNow, remaining));
            metadata.setPausedRemainingMillis(null);
            metadata.setUpdatedAt(System.currentTimeMillis());
            persistAudit(
                    session,
                    grave,
                    null,
                    "WORLD_RESTORED",
                    null,
                    GraveStatus.ORPHANED_WORLD,
                    target,
                    grave.itemEntryCount(),
                    grave.itemEntryCount(),
                    grave.remainingExperience(),
                    grave.remainingExperience(),
                    null
            );
            return true;
        }));
    }

    public CompletionStage<Boolean> relocate(Grave grave, GraveLocation location, GravePlacementType type) {
        return async(() -> orm.runInTransaction(session -> {
            GraveMetadataEntity metadata = session.find(
                    GraveMetadataEntity.class,
                    grave.graveId().toString(),
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (metadata == null || metadata.getOperationToken() != null) {
                return false;
            }
            metadata.setWorldUuid(location.worldUuid().toString());
            metadata.setWorldKey(location.worldKey());
            metadata.setGraveX(location.x());
            metadata.setGraveY(location.y());
            metadata.setGraveZ(location.z());
            metadata.setGraveYaw(location.yaw());
            metadata.setPlacementType(type.name());
            metadata.setUpdatedAt(System.currentTimeMillis());
            return true;
        }));
    }

    public CompletionStage<Boolean> acquireLease(
            String scopeKey,
            UUID ownerToken,
            long leaseDurationMillis
    ) {
        return async(() -> orm.runInTransaction(session -> {
            long now = System.currentTimeMillis();
            GraveLeaseEntity lease = session.find(
                    GraveLeaseEntity.class,
                    scopeKey,
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (lease == null) {
                lease = new GraveLeaseEntity();
                lease.setScopeKey(scopeKey);
                lease.setOwnerToken(ownerToken.toString());
                lease.setExpiresAt(now + leaseDurationMillis);
                lease.setUpdatedAt(now);
                session.persist(lease);
                return true;
            }
            if (lease.getExpiresAt() > now && !ownerToken.toString().equals(lease.getOwnerToken())) {
                return false;
            }
            lease.setOwnerToken(ownerToken.toString());
            lease.setExpiresAt(now + leaseDurationMillis);
            lease.setUpdatedAt(now);
            return true;
        }));
    }

    public CompletionStage<Boolean> renewLease(
            String scopeKey,
            UUID ownerToken,
            long leaseDurationMillis
    ) {
        return async(() -> orm.runInTransaction(session -> {
            long now = System.currentTimeMillis();
            GraveLeaseEntity lease = session.find(
                    GraveLeaseEntity.class,
                    scopeKey,
                    LockModeType.PESSIMISTIC_WRITE
            );
            if (lease == null || !ownerToken.toString().equals(lease.getOwnerToken())) {
                return false;
            }
            lease.setExpiresAt(now + leaseDurationMillis);
            lease.setUpdatedAt(now);
            return true;
        }));
    }

    public CompletionStage<Void> releaseLease(String scopeKey, UUID ownerToken) {
        return async(() -> {
            orm.runInTransaction(session -> {
                GraveLeaseEntity lease = session.find(
                        GraveLeaseEntity.class,
                        scopeKey,
                        LockModeType.PESSIMISTIC_WRITE
                );
                if (lease != null && ownerToken.toString().equals(lease.getOwnerToken())) {
                    lease.setExpiresAt(0L);
                    lease.setUpdatedAt(System.currentTimeMillis());
                }
                return null;
            });
            return null;
        });
    }

    private <T> CompletionStage<T> async(java.util.function.Supplier<T> operation) {
        return feature.getLifecycleManager().getTaskManager().supplyAsync(operation);
    }

    private boolean ownsOperation(GraveMetadataEntity metadata, UUID operationToken) {
        return metadata != null && operationToken.toString().equals(metadata.getOperationToken());
    }

    private GraveMetadataEntity toEntity(Grave grave, Long ownerPlayerId) {
        GraveMetadataEntity entity = new GraveMetadataEntity();
        entity.setGraveId(grave.graveId().toString());
        entity.setShortId(grave.shortId());
        entity.setOwnerUuid(grave.ownerUuid().toString());
        entity.setOwnerPlayerId(ownerPlayerId);
        entity.setOwnerName(grave.ownerName());
        entity.setServerId(grave.serverId());
        entity.setInventoryScope(grave.inventoryScope());
        entity.setWorldUuid(grave.location().worldUuid().toString());
        entity.setWorldKey(grave.location().worldKey());
        entity.setDeathX(grave.deathLocation().x());
        entity.setDeathY(grave.deathLocation().y());
        entity.setDeathZ(grave.deathLocation().z());
        entity.setGraveX(grave.location().x());
        entity.setGraveY(grave.location().y());
        entity.setGraveZ(grave.location().z());
        entity.setGraveYaw(grave.location().yaw());
        entity.setPlacementType(grave.placementType().name());
        entity.setState(grave.status().name());
        entity.setCreatedWallMillis(grave.createdWallMillis());
        entity.setCreatedActiveMillis(grave.createdActiveMillis());
        entity.setExpiresActiveMillis(grave.expiresActiveMillis());
        entity.setPausedRemainingMillis(grave.pausedRemainingMillis());
        entity.setItemEntryCount(grave.itemEntryCount());
        entity.setRemainingExperience(grave.remainingExperience());
        entity.setPayloadRevision(grave.payloadRevision());
        entity.setPayloadChecksum(grave.payloadChecksum());
        entity.setOwnerWasVanished(grave.ownerWasVanished());
        entity.setDeathCause(grave.deathCause());
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    private GravePayloadEntity toPayloadEntity(
            UUID graveId,
            long revision,
            EncodedGravePayload encoded
    ) {
        GravePayloadEntity entity = new GravePayloadEntity();
        entity.setGraveId(graveId.toString());
        entity.setPayloadRevision(revision);
        entity.setPayloadCodec(GravePayloadCodec.CODEC_VERSION);
        entity.setCompressed(false);
        entity.setPayload(encoded.bytes());
        entity.setPayloadChecksum(encoded.checksum());
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    private Grave toGrave(GraveMetadataEntity entity) {
        UUID worldUuid = UUID.fromString(entity.getWorldUuid());
        GraveLocation death = new GraveLocation(
                worldUuid,
                entity.getWorldKey(),
                entity.getDeathX(),
                entity.getDeathY(),
                entity.getDeathZ(),
                entity.getGraveYaw()
        );
        GraveLocation location = new GraveLocation(
                worldUuid,
                entity.getWorldKey(),
                entity.getGraveX(),
                entity.getGraveY(),
                entity.getGraveZ(),
                entity.getGraveYaw()
        );
        return new Grave(
                UUID.fromString(entity.getGraveId()),
                entity.getShortId(),
                UUID.fromString(entity.getOwnerUuid()),
                entity.getOwnerName(),
                entity.getServerId(),
                entity.getInventoryScope(),
                death,
                location,
                GravePlacementType.valueOf(entity.getPlacementType()),
                GraveStatus.valueOf(entity.getState()),
                entity.getCreatedWallMillis(),
                entity.getCreatedActiveMillis(),
                entity.getExpiresActiveMillis(),
                entity.getPausedRemainingMillis(),
                entity.getItemEntryCount(),
                entity.getRemainingExperience(),
                entity.getPayloadRevision(),
                entity.getPayloadChecksum(),
                entity.getDeathCause(),
                entity.isOwnerWasVanished()
        );
    }

    private void persistAudit(
            org.hibernate.Session session,
            Grave grave,
            UUID operationToken,
            String action,
            UUID actorUuid,
            GraveStatus oldStatus,
            GraveStatus newStatus,
            int itemsBefore,
            int itemsAfter,
            int experienceBefore,
            int experienceAfter,
            String details
    ) {
        GraveAuditEntity audit = new GraveAuditEntity();
        audit.setGraveId(grave.graveId().toString());
        audit.setOperationToken(operationToken == null ? null : operationToken.toString());
        audit.setAction(action);
        audit.setActorUuid(actorUuid == null ? null : actorUuid.toString());
        audit.setOldState(oldStatus == null ? null : oldStatus.name());
        audit.setNewState(newStatus == null ? null : newStatus.name());
        audit.setItemCountBefore(itemsBefore);
        audit.setItemCountAfter(itemsAfter);
        audit.setExperienceBefore(experienceBefore);
        audit.setExperienceAfter(experienceAfter);
        audit.setServerId(grave.serverId());
        audit.setDetails(details);
        audit.setCreatedAt(System.currentTimeMillis());
        session.persist(audit);
    }
}
