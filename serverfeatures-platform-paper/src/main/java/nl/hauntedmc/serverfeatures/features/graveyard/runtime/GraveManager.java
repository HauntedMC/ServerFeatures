package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.graveyard.ClaimReason;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveClaimOutcome;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveClaimResult;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveSnapshot;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveStatus;
import nl.hauntedmc.serverfeatures.api.graveyard.GraveyardService;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.capture.PlayerInventoryState;
import nl.hauntedmc.serverfeatures.features.graveyard.claim.ClaimTransferPlan;
import nl.hauntedmc.serverfeatures.features.graveyard.claim.GraveClaimPlanner;
import nl.hauntedmc.serverfeatures.features.graveyard.config.GraveyardSettings;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.CaptureJournalRecord;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.CaptureJournalState;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.ClaimJournalRecord;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.ClaimJournalState;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.GraveOperationJournal;
import nl.hauntedmc.serverfeatures.features.graveyard.journal.PlayerOperationReceiptService;
import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveLocation;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePlacementType;
import nl.hauntedmc.serverfeatures.features.graveyard.packet.GravePacketIdentity;
import nl.hauntedmc.serverfeatures.features.graveyard.packet.GravePacketRenderer;
import nl.hauntedmc.serverfeatures.features.graveyard.packet.GraveViewerState;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.EncodedGravePayload;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GravePayloadCodec;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.GraveRepository;
import nl.hauntedmc.serverfeatures.features.graveyard.placement.GravePlacementService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Runtime authority for grave indexing, visibility, expiry and effectively-once claims.
 */
public final class GraveManager implements GraveyardService {
    public static final String CLAIM_OTHERS_PERMISSION =
            "serverfeatures.feature.graveyard.claim.others";
    public static final String INSPECT_PERMISSION =
            "serverfeatures.feature.graveyard.admin.inspect";

    private static final Set<GraveStatus> CLAIMABLE_STATES = EnumSet.of(
            GraveStatus.ACTIVE,
            GraveStatus.PARTIAL,
            GraveStatus.ORPHANED_WORLD,
            GraveStatus.DELIVERY_PENDING
    );
    private static final Set<GraveStatus> EXPIRABLE_STATES = EnumSet.of(
            GraveStatus.ACTIVE,
            GraveStatus.PARTIAL
    );

    private final Graveyard feature;
    private final GraveyardSettings settings;
    private final GraveRepository repository;
    private final GraveOperationJournal journal;
    private final PlayerOperationReceiptService receipts;
    private final GravePayloadCodec payloadCodec;
    private final GraveClaimPlanner claimPlanner;
    private final GravePlacementService placementService;
    private final GravePacketRenderer renderer;
    private final GraveSpatialIndex spatialIndex = new GraveSpatialIndex();

    private final Map<UUID, Grave> graves = new ConcurrentHashMap<>();
    private final Map<String, UUID> shortIds = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ownerIndex = new ConcurrentHashMap<>();
    private final Map<UUID, GravePayload> payloadCache = new ConcurrentHashMap<>();
    private final Map<UUID, CaptureJournalRecord> unprojectedCaptures = new ConcurrentHashMap<>();
    private final Map<UUID, CaptureJournalRecord> unresolvedCaptures = new ConcurrentHashMap<>();
    private final Map<UUID, ClaimJournalRecord> pendingClaims = new ConcurrentHashMap<>();
    private final Map<UUID, ClaimJournalRecord> unresolvedClaims = new ConcurrentHashMap<>();

    private final Map<UUID, GravePacketIdentity> packetIdentities = new HashMap<>();
    private final Map<UUID, Map<UUID, GraveViewerState>> viewerStates = new HashMap<>();
    private final Map<Integer, InteractionHandle> interactionEntities = new ConcurrentHashMap<>();
    private final Set<UUID> activeOperations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> trackedGraves = new ConcurrentHashMap<>();
    private final Map<UUID, Long> unauthorizedMessageTimes = new ConcurrentHashMap<>();

    private final UUID leaseOwnerToken = UUID.randomUUID();
    private final String leaseScopeKey;
    private final AtomicBoolean mutable = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    public GraveManager(
            Graveyard feature,
            GraveyardSettings settings,
            GraveRepository repository,
            GraveOperationJournal journal,
            PlayerOperationReceiptService receipts,
            GravePayloadCodec payloadCodec,
            GravePlacementService placementService
    ) {
        this.feature = feature;
        this.settings = settings;
        this.repository = repository;
        this.journal = journal;
        this.receipts = receipts;
        this.payloadCodec = payloadCodec;
        this.claimPlanner = new GraveClaimPlanner(payloadCodec, settings.partialClaims());
        this.placementService = placementService;
        this.renderer = new GravePacketRenderer(settings, feature.getPlugin().getLogger());
        this.leaseScopeKey = settings.serverId() + ":" + settings.inventoryScope();
    }

    public void initialize() {
        recoverLocalJournals();
        repository.loadRuntimeGraves(settings.serverId(), settings.inventoryScope())
                .whenComplete((loaded, failure) -> runMain(() -> {
                    if (failure != null) {
                        feature.getLogger().log(Level.SEVERE, "Could not load persisted Graveyard graves.", failure);
                        return;
                    }
                    for (Grave grave : loaded) {
                        activate(grave);
                    }
                    initializeOnlinePlayers();
                }));
        acquireLease();
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(
                this::tick,
                BukkitTime.ticks(settings.reconciliationTicks()),
                BukkitTime.ticks(settings.reconciliationTicks())
        );
        long heartbeatTicks = Math.max(20L, (settings.leaseHeartbeatMillis() + 49L) / 50L);
        feature.getLifecycleManager().getTaskManager().scheduleAsyncRepeatingTask(
                this::renewLease,
                BukkitTime.ticks(heartbeatTicks),
                BukkitTime.ticks(heartbeatTicks)
        );
    }

    public boolean canMutate() {
        return mutable.get() && !shuttingDown.get();
    }

    public boolean isInteractionEntity(int entityId) {
        return interactionEntities.containsKey(entityId);
    }

    public void acceptCommittedCapture(CaptureJournalRecord record, GravePayload payload) {
        payloadCache.put(record.grave().graveId(), payload);
        unprojectedCaptures.put(record.grave().graveId(), record);
        activate(record.grave());
        projectCapture(record);
    }

    public void handleInteraction(Player player, int entityId) {
        InteractionHandle handle = interactionEntities.get(entityId);
        if (handle == null || !player.isOnline()) {
            return;
        }
        Grave grave = graves.get(handle.graveId());
        GravePacketIdentity identity = packetIdentities.get(handle.graveId());
        GraveViewerState state = viewerStates.getOrDefault(handle.graveId(), Map.of())
                .get(player.getUniqueId());
        if (grave == null
                || identity == null
                || state == null
                || !state.spawned()
                || handle.generation() != identity.generation()
                || state.generation() != identity.generation()) {
            return;
        }
        if (!validatePhysicalInteraction(player, grave)) {
            return;
        }
        if (grave.ownerUuid().equals(player.getUniqueId())) {
            requestClaimForPlayer(grave, player, player, ClaimReason.PHYSICAL_INTERACTION, null);
            return;
        }
        if (player.hasPermission(CLAIM_OTHERS_PERMISSION)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.staff_use_deliver")
                    .with("grave_id", grave.shortId())
                    .with("player", grave.ownerName())
                    .forAudience(player)
                    .build());
            return;
        }
        long now = System.currentTimeMillis();
        long previous = unauthorizedMessageTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous >= 3_000L) {
            unauthorizedMessageTimes.put(player.getUniqueId(), now);
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("graveyard.not_owner")
                    .with("player", grave.ownerName())
                    .forAudience(player)
                    .build());
        }
    }

    @Override
    public Optional<GraveSnapshot> find(UUID graveId) {
        Grave grave = graves.get(graveId);
        return grave == null
                ? Optional.empty()
                : Optional.of(grave.snapshot(feature.getPlugin().getServerActiveClock().nowMillis()));
    }

    @Override
    public Optional<GraveSnapshot> findByShortId(String shortId) {
        UUID graveId = shortIds.get(normalizeShortId(shortId));
        return graveId == null ? Optional.empty() : find(graveId);
    }

    public Optional<Grave> findRuntime(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        UUID byShort = shortIds.get(normalizeShortId(identifier));
        if (byShort != null) {
            return Optional.ofNullable(graves.get(byShort));
        }
        try {
            return Optional.ofNullable(graves.get(UUID.fromString(identifier)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<GraveSnapshot> findActiveByOwner(UUID ownerUuid) {
        long activeNow = feature.getPlugin().getServerActiveClock().nowMillis();
        return ownerIndex.getOrDefault(ownerUuid, Set.of()).stream()
                .map(graves::get)
                .filter(java.util.Objects::nonNull)
                .filter(grave -> grave.status().hasRecoverablePayload())
                .sorted(Comparator.comparingLong(Grave::createdWallMillis).reversed())
                .map(grave -> grave.snapshot(activeNow))
                .toList();
    }

    public List<GraveSnapshot> allRuntimeGraves() {
        long activeNow = feature.getPlugin().getServerActiveClock().nowMillis();
        return graves.values().stream()
                .sorted(Comparator.comparingLong(Grave::createdWallMillis).reversed())
                .map(grave -> grave.snapshot(activeNow))
                .toList();
    }

    @Override
    public CompletionStage<GraveClaimResult> requestClaim(
            UUID graveId,
            UUID ownerUuid,
            ClaimReason reason
    ) {
        CompletableFuture<GraveClaimResult> result = new CompletableFuture<>();
        runMain(() -> {
            Grave grave = graves.get(graveId);
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (grave == null || owner == null || !owner.isOnline()) {
                result.complete(result(graveId, GraveClaimOutcome.NOT_FOUND, "Owner or grave is unavailable"));
                return;
            }
            if (!grave.ownerUuid().equals(ownerUuid)) {
                result.complete(result(graveId, GraveClaimOutcome.NOT_OWNER, "Owner mismatch"));
                return;
            }
            if (reason == ClaimReason.REMOTE_UNREACHABLE
                    && grave.placementType() != GravePlacementType.REMOTE_ONLY
                    && grave.status() != GraveStatus.ORPHANED_WORLD) {
                result.complete(result(
                        graveId,
                        GraveClaimOutcome.NOT_CLAIMABLE,
                        "This grave must be claimed at its location"
                ));
                return;
            }
            requestClaimForPlayer(grave, owner, owner, reason, result);
        });
        return result;
    }

    public CompletionStage<GraveClaimResult> deliver(Player actor, Grave grave) {
        CompletableFuture<GraveClaimResult> result = new CompletableFuture<>();
        runMain(() -> {
            if (!actor.hasPermission(CLAIM_OTHERS_PERMISSION)) {
                result.complete(result(grave.graveId(), GraveClaimOutcome.NOT_OWNER, "No permission"));
                return;
            }
            Player owner = Bukkit.getPlayer(grave.ownerUuid());
            if (owner != null && owner.isOnline()) {
                requestClaimForPlayer(grave, owner, actor, ClaimReason.STAFF_DELIVERY, result);
                return;
            }
            queuePendingDelivery(actor, grave, result);
        });
        return result;
    }

    public CompletionStage<Boolean> expire(Player actor, Grave grave) {
        return transitionAndApply(
                actor,
                grave,
                EnumSet.of(GraveStatus.ACTIVE, GraveStatus.PARTIAL, GraveStatus.ORPHANED_WORLD),
                GraveStatus.EXPIRED,
                "ADMIN_EXPIRED"
        );
    }

    public CompletionStage<Boolean> restore(Player actor, Grave grave) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        repository.restore(
                grave,
                actor == null ? null : actor.getUniqueId(),
                feature.getPlugin().getServerActiveClock().nowMillis(),
                settings.lifetimeMillis()
        ).whenComplete((success, failure) -> runMain(() -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                result.complete(false);
                return;
            }
            grave.restore(feature.getPlugin().getServerActiveClock().nowMillis(), settings.lifetimeMillis());
            activate(grave);
            result.complete(true);
        }));
        return result;
    }

    public CompletionStage<Boolean> purge(Player actor, Grave grave) {
        return transitionAndApply(
                actor,
                grave,
                EnumSet.of(GraveStatus.EXPIRED, GraveStatus.CORRUPT, GraveStatus.ADMIN_RECOVERED),
                GraveStatus.PURGED,
                "PURGED"
        );
    }

    public boolean track(Player player, Grave grave) {
        if (!grave.ownerUuid().equals(player.getUniqueId()) && !player.hasPermission(INSPECT_PERMISSION)) {
            return false;
        }
        trackedGraves.put(player.getUniqueId(), grave.graveId());
        return true;
    }

    public void stopTracking(Player player) {
        trackedGraves.remove(player.getUniqueId());
    }

    public void onPlayerJoin(Player player) {
        recoverCaptureReceipt(player);
        recoverClaimReceipt(player);
        GravesForOwner:
        for (UUID graveId : ownerIndex.getOrDefault(player.getUniqueId(), Set.of())) {
            Grave grave = graves.get(graveId);
            if (grave != null && grave.status() == GraveStatus.DELIVERY_PENDING) {
                requestClaimForPlayer(grave, player, player, ClaimReason.PENDING_DELIVERY, null);
                break GravesForOwner;
            }
        }
        reconcileViewer(player);
    }

    public void onPlayerQuit(Player player) {
        hideViewer(player);
        trackedGraves.remove(player.getUniqueId());
        unauthorizedMessageTimes.remove(player.getUniqueId());
    }

    public void onPlayerTransition(Player player) {
        hideViewer(player);
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                () -> reconcileViewer(player),
                BukkitTime.ticks(settings.spawnSettleTicks())
        );
    }

    public void onWorldUnload(World world) {
        for (Grave grave : graves.values()) {
            if (grave.location().worldUuid().equals(world.getUID())) {
                hideGrave(grave.graveId());
                spatialIndex.remove(grave.graveId());
            }
        }
    }

    public void onWorldLoad(World world) {
        long activeNow = feature.getPlugin().getServerActiveClock().nowMillis();
        for (Grave grave : graves.values()) {
            if (!grave.location().worldUuid().equals(world.getUID())
                    || !grave.location().worldKey().equals(world.getKey().asString())) {
                continue;
            }
            if (grave.status() == GraveStatus.ORPHANED_WORLD && grave.pausedRemainingMillis() != null) {
                repository.resumeOrphaned(grave, activeNow).whenComplete((success, failure) -> runMain(() -> {
                    if (failure != null || !Boolean.TRUE.equals(success)) {
                        return;
                    }
                    grave.resume(activeNow);
                    activate(grave);
                    reconcileNearby(grave);
                }));
            } else if (grave.status().isVisible()) {
                spatialIndex.put(grave);
                reconcileNearby(grave);
            }
        }
    }

    public CompletionStage<Boolean> relocate(Player actor, Grave grave, Location requested) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Optional<nl.hauntedmc.serverfeatures.features.graveyard.placement.GravePlacementResult> placement =
                placementService.validateRelocation(requested);
        if (placement.isEmpty()) {
            result.complete(false);
            return result;
        }
        GraveLocation oldLocation = grave.location();
        var next = placement.get();
        repository.relocate(grave, next.location(), next.type()).whenComplete((success, failure) -> runMain(() -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                result.complete(false);
                return;
            }
            spatialIndex.remove(grave.graveId());
            hideGrave(grave.graveId());
            grave.relocate(next.location(), next.type());
            if (grave.status().isVisible()) {
                spatialIndex.put(grave);
                reconcileNearby(grave);
            }
            feature.getLogger().info(
                    "Relocated grave " + grave.graveId() + " from " + oldLocation + " to " + next.location()
                            + " by " + actor.getName()
            );
            result.complete(true);
        }));
        return result;
    }

    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        mutable.set(false);
        destroyAllPacketEntities();
        spatialIndex.clear();
        repository.releaseLease(leaseScopeKey, leaseOwnerToken);
    }

    public String diagnostics() {
        return "mutable=" + mutable.get()
                + ", graves=" + graves.size()
                + ", visiblePairs=" + visiblePairCount()
                + ", unprojected=" + unprojectedCaptures.size()
                + ", unresolvedCaptures=" + unresolvedCaptures.size()
                + ", pendingClaims=" + pendingClaims.size()
                + ", activeOperations=" + activeOperations.size();
    }

    private void requestClaimForPlayer(
            Grave grave,
            Player owner,
            Player actor,
            ClaimReason reason,
            CompletableFuture<GraveClaimResult> externalResult
    ) {
        CompletableFuture<GraveClaimResult> result = externalResult == null
                ? new CompletableFuture<>()
                : externalResult;
        if (!canMutate()) {
            completeClaim(result, grave, GraveClaimOutcome.FAILED, 0, 0, 0, "Graveyard is read-only");
            return;
        }
        if (!grave.ownerUuid().equals(owner.getUniqueId())) {
            completeClaim(result, grave, GraveClaimOutcome.NOT_OWNER, 0, 0, 0, "Owner mismatch");
            return;
        }
        if (!settings.inventoryScope().equals(grave.inventoryScope())) {
            completeClaim(result, grave, GraveClaimOutcome.WRONG_INVENTORY_SCOPE, 0, 0, 0, "Wrong inventory scope");
            return;
        }
        if (!CLAIMABLE_STATES.contains(grave.status())) {
            completeClaim(result, grave, GraveClaimOutcome.NOT_CLAIMABLE, 0, 0, 0, "Grave is not claimable");
            return;
        }
        if (!activeOperations.add(grave.graveId())) {
            completeClaim(result, grave, GraveClaimOutcome.BUSY, 0, 0, 0, "Claim already in progress");
            return;
        }

        UUID operationToken = UUID.randomUUID();
        repository.reserveOperation(grave.graveId(), operationToken, CLAIMABLE_STATES)
                .whenComplete((reserved, reserveFailure) -> {
                    if (reserveFailure != null || !Boolean.TRUE.equals(reserved)) {
                        runMain(() -> {
                            activeOperations.remove(grave.graveId());
                            completeClaim(result, grave, GraveClaimOutcome.BUSY, 0, 0, 0, "Could not reserve grave");
                        });
                        return;
                    }
                    loadDecodedPayload(grave).whenComplete((payload, payloadFailure) -> runMain(() -> {
                        if (payloadFailure != null || payload == null) {
                            releaseFailedClaim(grave, operationToken, result, payloadFailure);
                            return;
                        }
                        applyClaim(grave, owner, actor, reason, operationToken, payload, result);
                    }));
                });
    }

    private void applyClaim(
            Grave grave,
            Player owner,
            Player actor,
            ClaimReason reason,
            UUID operationToken,
            GravePayload payload,
            CompletableFuture<GraveClaimResult> result
    ) {
        if (!owner.isOnline()) {
            releaseFailedClaim(grave, operationToken, result, null);
            return;
        }
        PlayerInventoryState beforeInventory = PlayerInventoryState.capture(owner);
        int beforeExperience = owner.calculateTotalExperiencePoints();
        try {
            ClaimTransferPlan plan = claimPlanner.plan(beforeInventory, payload);
            if (!plan.changed()) {
                repository.releaseOperation(grave.graveId(), operationToken);
                activeOperations.remove(grave.graveId());
                completeClaim(
                        result,
                        grave,
                        GraveClaimOutcome.NOTHING_FIT,
                        0,
                        payload.entries().size(),
                        0,
                        "No inventory space"
                );
                owner.sendMessage(feature.getLocalizationHandler()
                        .getMessage("graveyard.inventory_full")
                        .with("grave_id", grave.shortId())
                        .forAudience(owner)
                        .build());
                return;
            }

            EncodedGravePayload encodedRemaining = payloadCodec.encode(plan.remainingPayload());
            ClaimJournalRecord prepared = new ClaimJournalRecord(
                    operationToken,
                    ClaimJournalState.PREPARED,
                    grave.graveId(),
                    owner.getUniqueId(),
                    actor.getUniqueId(),
                    payload.revision(),
                    plan.transferredEntries(),
                    plan.transferredExperience(),
                    encodedRemaining
            );
            journal.writeClaim(prepared);
            receipts.putClaim(owner, operationToken, grave.graveId());
            plan.resultingInventory().apply(owner);
            if (plan.transferredExperience() > 0) {
                owner.giveExp(plan.transferredExperience());
            }
            owner.saveData();
            ClaimJournalRecord applied = prepared.withState(ClaimJournalState.PLAYER_APPLIED);
            try {
                journal.writeClaim(applied);
                pendingClaims.put(operationToken, applied);
            } catch (IOException journalFailure) {
                unresolvedClaims.put(operationToken, prepared);
                feature.getLogger().log(
                        Level.SEVERE,
                        "Playerdata was saved for claim " + operationToken
                                + " but its PLAYER_APPLIED journal transition failed. "
                                + "The persisted player receipt will drive recovery.",
                        journalFailure
                );
            }
            hideGrave(grave.graveId());
            finalizeAppliedClaim(grave, owner, actor, payload, plan, applied, result, reason);
        } catch (IOException | RuntimeException exception) {
            beforeInventory.apply(owner);
            owner.setExperienceLevelAndProgress(beforeExperience);
            receipts.clearClaim(owner);
            repository.releaseOperation(grave.graveId(), operationToken);
            activeOperations.remove(grave.graveId());
            feature.getLogger().log(Level.SEVERE, "Could not apply claim for grave " + grave.graveId(), exception);
            completeClaim(result, grave, GraveClaimOutcome.FAILED, 0, payload.entries().size(), 0, "Claim failed");
        }
    }

    private void finalizeAppliedClaim(
            Grave grave,
            Player owner,
            Player actor,
            GravePayload previous,
            ClaimTransferPlan plan,
            ClaimJournalRecord journalRecord,
            CompletableFuture<GraveClaimResult> result,
            ClaimReason reason
    ) {
        GraveStatus finalStatus = plan.remainingPayload().isEmpty()
                ? GraveStatus.CLAIMED
                : GraveStatus.PARTIAL;
        repository.finalizeClaim(
                grave,
                journalRecord.operationToken(),
                actor.getUniqueId(),
                previous,
                plan.remainingPayload(),
                journalRecord.remainingPayload(),
                finalStatus,
                plan.transferredEntries(),
                plan.transferredExperience()
        ).whenComplete((success, failure) -> runMain(() -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                feature.getLogger().log(
                        Level.SEVERE,
                        "Claim " + journalRecord.operationToken() + " was saved to playerdata but not finalized; "
                                + "the recovery journal will retry it.",
                        failure
                );
                activeOperations.remove(grave.graveId());
                completeClaim(
                        result,
                        grave,
                        finalStatus == GraveStatus.CLAIMED
                                ? GraveClaimOutcome.CLAIMED
                                : GraveClaimOutcome.PARTIAL,
                        plan.transferredEntries(),
                        plan.remainingPayload().entries().size(),
                        plan.transferredExperience(),
                        "Claim applied; durable database finalization is pending"
                );
                return;
            }
            completeFinalizedClaim(
                    grave,
                    owner,
                    plan.remainingPayload(),
                    journalRecord,
                    finalStatus,
                    result,
                    reason
            );
        }));
    }

    private void completeFinalizedClaim(
            Grave grave,
            Player owner,
            GravePayload remaining,
            ClaimJournalRecord journalRecord,
            GraveStatus finalStatus,
            CompletableFuture<GraveClaimResult> result,
            ClaimReason reason
    ) {
        grave.updatePayload(remaining, journalRecord.remainingPayload().checksum(), finalStatus);
        payloadCache.put(grave.graveId(), remaining);
        pendingClaims.remove(journalRecord.operationToken());
        unresolvedClaims.remove(journalRecord.operationToken());
        activeOperations.remove(grave.graveId());
        receipts.clearClaim(owner);
        try {
            owner.saveData();
        } catch (RuntimeException exception) {
            feature.getLogger().warning("Could not persist claim-receipt cleanup for " + owner.getName());
        }
        try {
            journal.writeClaim(journalRecord.withState(ClaimJournalState.GRAVE_FINALIZED));
            journal.deleteClaim(journalRecord.operationToken());
        } catch (IOException exception) {
            feature.getLogger().log(Level.WARNING, "Could not clean finalized Graveyard claim journal.", exception);
        }

        if (finalStatus == GraveStatus.CLAIMED) {
            spatialIndex.remove(grave.graveId());
            trackedGraves.values().removeIf(grave.graveId()::equals);
            playEffect(grave, settings.claimParticle(), settings.claimSound(), 24);
        } else {
            spatialIndex.put(grave);
            reconcileNearby(grave);
        }
        owner.sendMessage(feature.getLocalizationHandler()
                .getMessage(finalStatus == GraveStatus.CLAIMED
                        ? "graveyard.claimed"
                        : "graveyard.partially_claimed")
                .with("grave_id", grave.shortId())
                .with("remaining", Integer.toString(remaining.entries().size()))
                .forAudience(owner)
                .build());
        completeClaim(
                result,
                grave,
                finalStatus == GraveStatus.CLAIMED
                        ? GraveClaimOutcome.CLAIMED
                        : GraveClaimOutcome.PARTIAL,
                journalRecord.transferredEntries(),
                remaining.entries().size(),
                journalRecord.transferredExperience(),
                reason.name()
        );
    }

    private void queuePendingDelivery(
            Player actor,
            Grave grave,
            CompletableFuture<GraveClaimResult> result
    ) {
        long remaining = grave.remainingActiveMillis(feature.getPlugin().getServerActiveClock().nowMillis());
        repository.pauseForState(
                grave,
                EnumSet.of(GraveStatus.ACTIVE, GraveStatus.PARTIAL, GraveStatus.ORPHANED_WORLD),
                GraveStatus.DELIVERY_PENDING,
                remaining,
                actor.getUniqueId(),
                "DELIVERY_PENDING"
        ).whenComplete((success, failure) -> runMain(() -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                result.complete(result(grave.graveId(), GraveClaimOutcome.FAILED, "Could not queue delivery"));
                return;
            }
            grave.pause(remaining, GraveStatus.DELIVERY_PENDING);
            hideGrave(grave.graveId());
            spatialIndex.remove(grave.graveId());
            result.complete(result(grave.graveId(), GraveClaimOutcome.CLAIMED, "Delivery queued"));
        }));
    }

    private CompletionStage<Boolean> transitionAndApply(
            Player actor,
            Grave grave,
            Set<GraveStatus> expected,
            GraveStatus target,
            String action
    ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        repository.transitionState(
                grave,
                expected,
                target,
                actor == null ? null : actor.getUniqueId(),
                action,
                null
        ).whenComplete((success, failure) -> runMain(() -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                result.complete(false);
                return;
            }
            grave.setStatus(target);
            if (!target.isVisible()) {
                hideGrave(grave.graveId());
                spatialIndex.remove(grave.graveId());
            }
            if (target == GraveStatus.EXPIRED) {
                playEffect(grave, settings.expiryParticle(), settings.expirySound(), 32);
            }
            result.complete(true);
        }));
        return result;
    }

    private CompletionStage<GravePayload> loadDecodedPayload(Grave grave) {
        GravePayload cached = payloadCache.get(grave.graveId());
        if (cached != null && cached.revision() == grave.payloadRevision()) {
            return CompletableFuture.completedFuture(cached);
        }
        return repository.loadPayload(grave.graveId()).thenApply(encoded -> {
            if (encoded.isEmpty()) {
                throw new IllegalStateException("Missing payload for grave " + grave.graveId());
            }
            try {
                GravePayload payload = payloadCodec.decode(encoded.get().bytes(), encoded.get().checksum());
                payloadCache.put(grave.graveId(), payload);
                return payload;
            } catch (IOException exception) {
                throw new IllegalStateException("Corrupt payload for grave " + grave.graveId(), exception);
            }
        });
    }

    private void releaseFailedClaim(
            Grave grave,
            UUID operationToken,
            CompletableFuture<GraveClaimResult> result,
            Throwable failure
    ) {
        repository.releaseOperation(grave.graveId(), operationToken);
        activeOperations.remove(grave.graveId());
        if (failure != null) {
            feature.getLogger().log(Level.SEVERE, "Could not load grave payload " + grave.graveId(), failure);
        }
        completeClaim(result, grave, GraveClaimOutcome.FAILED, 0, grave.itemEntryCount(), 0, "Claim failed");
    }

    private void completeClaim(
            CompletableFuture<GraveClaimResult> result,
            Grave grave,
            GraveClaimOutcome outcome,
            int transferredEntries,
            int remainingEntries,
            int transferredExperience,
            String message
    ) {
        if (result != null && !result.isDone()) {
            result.complete(new GraveClaimResult(
                    grave.graveId(),
                    outcome,
                    transferredEntries,
                    remainingEntries,
                    transferredExperience,
                    message
            ));
        }
    }

    private GraveClaimResult result(UUID graveId, GraveClaimOutcome outcome, String message) {
        return new GraveClaimResult(graveId, outcome, 0, 0, 0, message);
    }

    private void acquireLease() {
        repository.acquireLease(leaseScopeKey, leaseOwnerToken, settings.leaseDurationMillis())
                .whenComplete((acquired, failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(acquired)) {
                        mutable.set(false);
                        feature.getLogger().log(
                                Level.SEVERE,
                                "Graveyard could not acquire the instance lease for " + leaseScopeKey
                                        + "; deaths will retain vanilla behaviour.",
                                failure
                        );
                        return;
                    }
                    mutable.set(true);
                    feature.getLogger().info("Acquired Graveyard instance lease for " + leaseScopeKey);
                });
    }

    private void renewLease() {
        if (shuttingDown.get()) {
            return;
        }
        repository.renewLease(leaseScopeKey, leaseOwnerToken, settings.leaseDurationMillis())
                .whenComplete((renewed, failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(renewed)) {
                        if (mutable.getAndSet(false)) {
                            feature.getLogger().log(
                                    Level.SEVERE,
                                    "Graveyard lost its instance lease; all mutations are now disabled.",
                                    failure
                            );
                        }
                    } else {
                        mutable.set(true);
                    }
                });
    }

    private void tick() {
        if (shuttingDown.get()) {
            return;
        }
        expireDueGraves();
        retryUnprojectedCaptures();
        retryPendingClaimsForOnlineOwners();
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcileViewer(player);
            updateTracking(player);
        }
    }

    private void expireDueGraves() {
        if (!canMutate()) {
            return;
        }
        long activeNow = feature.getPlugin().getServerActiveClock().nowMillis();
        for (Grave grave : graves.values()) {
            if (!EXPIRABLE_STATES.contains(grave.status())
                    || grave.remainingActiveMillis(activeNow) > 0L
                    || activeOperations.contains(grave.graveId())) {
                continue;
            }
            if (!activeOperations.add(grave.graveId())) {
                continue;
            }
            repository.transitionState(
                    grave,
                    EXPIRABLE_STATES,
                    GraveStatus.EXPIRED,
                    null,
                    "EXPIRED",
                    null
            ).whenComplete((success, failure) -> runMain(() -> {
                activeOperations.remove(grave.graveId());
                if (failure != null || !Boolean.TRUE.equals(success)) {
                    return;
                }
                grave.setStatus(GraveStatus.EXPIRED);
                hideGrave(grave.graveId());
                spatialIndex.remove(grave.graveId());
                playEffect(grave, settings.expiryParticle(), settings.expirySound(), 32);
            }));
        }
    }

    private void reconcileViewer(Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        int radiusChunks = (int) Math.ceil(settings.despawnDistance() / 16.0);
        List<Grave> candidates = spatialIndex.nearby(
                        viewer.getWorld().getUID(),
                        viewer.getLocation().getBlockX() >> 4,
                        viewer.getLocation().getBlockZ() >> 4,
                        radiusChunks
                ).stream()
                .map(graves::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing((Grave grave) -> !grave.ownerUuid().equals(viewer.getUniqueId()))
                        .thenComparingDouble(grave -> distanceSquared(viewer, grave)))
                .toList();

        Set<UUID> desired = new HashSet<>();
        int rendered = 0;
        for (Grave grave : candidates) {
            GraveViewerState current = viewerStates
                    .computeIfAbsent(grave.graveId(), ignored -> new HashMap<>())
                    .get(viewer.getUniqueId());
            boolean currentlySpawned = current != null && current.spawned();
            if (rendered >= settings.maxRenderedPerViewer()
                    || !shouldShow(viewer, grave, currentlySpawned)) {
                continue;
            }
            desired.add(grave.graveId());
            rendered++;
            ensureShown(grave, viewer);
        }

        for (Map.Entry<UUID, Map<UUID, GraveViewerState>> entry : viewerStates.entrySet()) {
            GraveViewerState state = entry.getValue().get(viewer.getUniqueId());
            if (state != null && state.spawned() && !desired.contains(entry.getKey())) {
                hide(entry.getKey(), viewer);
            }
        }
    }

    private boolean shouldShow(Player viewer, Grave grave, boolean currentlySpawned) {
        if (!grave.status().isVisible()
                || grave.placementType() == GravePlacementType.REMOTE_ONLY
                || !viewer.getWorld().getUID().equals(grave.location().worldUuid())) {
            return false;
        }
        Optional<Location> resolved = grave.location().resolve();
        if (resolved.isEmpty()) {
            return false;
        }
        if (!viewer.isChunkSent(Chunk.getChunkKey(grave.location().chunkX(), grave.location().chunkZ()))) {
            return false;
        }
        double maximum = currentlySpawned ? settings.despawnDistance() : settings.spawnDistance();
        if (viewer.getLocation().distanceSquared(resolved.get()) > maximum * maximum) {
            return false;
        }
        return !grave.ownerWasVanished()
                || grave.ownerUuid().equals(viewer.getUniqueId())
                || viewer.hasPermission(INSPECT_PERMISSION);
    }

    private void ensureShown(Grave grave, Player viewer) {
        GravePacketIdentity identity = packetIdentities.computeIfAbsent(
                grave.graveId(),
                ignored -> createPacketIdentity(grave)
        );
        Map<UUID, GraveViewerState> graveViewers = viewerStates.computeIfAbsent(
                grave.graveId(),
                ignored -> new HashMap<>()
        );
        GraveViewerState state = graveViewers.computeIfAbsent(
                viewer.getUniqueId(),
                ignored -> new GraveViewerState()
        );
        String timer = timerText(grave);
        if (!state.spawned() || state.generation() != identity.generation()) {
            if (state.spawned()) {
                renderer.destroy(identity, viewer);
            }
            try {
                renderer.spawn(grave, identity, viewer, timer, glowColor(grave, viewer));
                state.markSpawned(identity.generation(), timer);
            } catch (RuntimeException exception) {
                state.markHidden();
                feature.getLogger().warning(
                        "Could not render grave " + grave.graveId() + " for " + viewer.getName()
                                + ": " + exception.getMessage()
                );
            }
            return;
        }
        if (!timer.equals(state.renderedTimer())) {
            try {
                renderer.updateTimer(grave, identity, viewer, timer);
                state.setRenderedTimer(timer);
            } catch (RuntimeException exception) {
                feature.getLogger().warning(
                        "Could not update grave timer " + grave.graveId() + " for " + viewer.getName()
                );
            }
        }
    }

    private GravePacketIdentity createPacketIdentity(Grave grave) {
        GravePacketIdentity identity = GravePacketIdentity.create(grave.rotateVisualGeneration());
        interactionEntities.put(
                identity.interactionEntityId(),
                new InteractionHandle(grave.graveId(), identity.generation())
        );
        return identity;
    }

    private void hide(UUID graveId, Player viewer) {
        GravePacketIdentity identity = packetIdentities.get(graveId);
        Map<UUID, GraveViewerState> states = viewerStates.get(graveId);
        GraveViewerState state = states == null ? null : states.get(viewer.getUniqueId());
        if (identity != null && state != null && state.spawned()) {
            renderer.destroy(identity, viewer);
            state.markHidden();
        }
    }

    private void hideGrave(UUID graveId) {
        GravePacketIdentity identity = packetIdentities.remove(graveId);
        if (identity != null) {
            interactionEntities.remove(identity.interactionEntityId());
        }
        Map<UUID, GraveViewerState> states = viewerStates.remove(graveId);
        if (identity == null || states == null) {
            return;
        }
        for (Map.Entry<UUID, GraveViewerState> entry : states.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && entry.getValue().spawned()) {
                renderer.destroy(identity, viewer);
            }
        }
    }

    private void hideViewer(Player viewer) {
        for (UUID graveId : new ArrayList<>(viewerStates.keySet())) {
            hide(graveId, viewer);
            Map<UUID, GraveViewerState> states = viewerStates.get(graveId);
            if (states != null) {
                states.remove(viewer.getUniqueId());
            }
        }
    }

    private void destroyAllPacketEntities() {
        for (UUID graveId : new ArrayList<>(packetIdentities.keySet())) {
            hideGrave(graveId);
        }
        packetIdentities.clear();
        viewerStates.clear();
        interactionEntities.clear();
    }

    private void activate(Grave grave) {
        Grave previous = graves.put(grave.graveId(), grave);
        if (previous != null && previous != grave) {
            hideGrave(previous.graveId());
            spatialIndex.remove(previous.graveId());
        }
        shortIds.put(normalizeShortId(grave.shortId()), grave.graveId());
        ownerIndex.computeIfAbsent(grave.ownerUuid(), ignored -> ConcurrentHashMap.newKeySet())
                .add(grave.graveId());
        if (grave.status().isVisible()
                && grave.placementType() != GravePlacementType.REMOTE_ONLY
                && grave.location().resolve().isPresent()) {
            spatialIndex.put(grave);
        } else if (grave.status().isVisible() && grave.location().resolve().isEmpty()) {
            grave.pause(
                    grave.remainingActiveMillis(feature.getPlugin().getServerActiveClock().nowMillis()),
                    GraveStatus.ORPHANED_WORLD
            );
        }
    }

    private void projectCapture(CaptureJournalRecord record) {
        repository.saveCaptured(record.grave(), record.payload())
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        feature.getLogger().log(
                                Level.WARNING,
                                "Grave " + record.grave().graveId()
                                        + " remains journal-backed because database projection failed.",
                                failure
                        );
                        return;
                    }
                    try {
                        journal.writeCapture(record.withState(CaptureJournalState.PROJECTED));
                        journal.deleteCapture(record.grave().graveId());
                        unprojectedCaptures.remove(record.grave().graveId());
                    } catch (IOException exception) {
                        feature.getLogger().log(Level.WARNING, "Could not clean projected capture journal.", exception);
                    }
                });
    }

    private void retryUnprojectedCaptures() {
        if (!canMutate()) {
            return;
        }
        for (CaptureJournalRecord record : List.copyOf(unprojectedCaptures.values())) {
            projectCapture(record);
        }
    }

    private void recoverLocalJournals() {
        try {
            for (CaptureJournalRecord record : journal.loadCaptures()) {
                switch (record.state()) {
                    case COMMITTED -> {
                        GravePayload payload = payloadCodec.decode(
                                record.payload().bytes(),
                                record.payload().checksum()
                        );
                        payloadCache.put(record.grave().graveId(), payload);
                        unprojectedCaptures.put(record.grave().graveId(), record);
                        activate(record.grave());
                    }
                    case PROJECTED, ABORTED -> journal.deleteCapture(record.grave().graveId());
                    case PREPARED -> unresolvedCaptures.put(record.operationToken(), record);
                }
            }
            for (ClaimJournalRecord record : journal.loadClaims()) {
                switch (record.state()) {
                    case PLAYER_APPLIED -> pendingClaims.put(record.operationToken(), record);
                    case PREPARED -> unresolvedClaims.put(record.operationToken(), record);
                    case GRAVE_FINALIZED, RECOVERED, ABORTED -> journal.deleteClaim(record.operationToken());
                }
            }
        } catch (IOException exception) {
            feature.getLogger().log(Level.SEVERE, "Could not recover Graveyard operation journals.", exception);
        }
    }

    private void recoverCaptureReceipt(Player player) {
        Optional<PlayerOperationReceiptService.Receipt> receipt = receipts.capture(player);
        if (receipt.isPresent()) {
            CaptureJournalRecord record = unresolvedCaptures.remove(receipt.get().operationToken());
            if (record != null && record.grave().graveId().equals(receipt.get().graveId())) {
                try {
                    CaptureJournalRecord committed = record.withState(CaptureJournalState.COMMITTED);
                    journal.writeCapture(committed);
                    GravePayload payload = payloadCodec.decode(
                            committed.payload().bytes(),
                            committed.payload().checksum()
                    );
                    acceptCommittedCapture(committed, payload);
                } catch (IOException exception) {
                    feature.getLogger().log(Level.SEVERE, "Could not recover committed grave capture.", exception);
                }
            }
            receipts.clearCapture(player);
            player.saveData();
        }

        for (CaptureJournalRecord record : List.copyOf(unresolvedCaptures.values())) {
            if (!record.grave().ownerUuid().equals(player.getUniqueId())) {
                continue;
            }
            unresolvedCaptures.remove(record.operationToken());
            try {
                journal.writeCapture(record.withState(CaptureJournalState.ABORTED));
                journal.deleteCapture(record.grave().graveId());
            } catch (IOException exception) {
                feature.getLogger().warning("Could not clean aborted capture " + record.grave().graveId());
            }
        }
    }

    private void recoverClaimReceipt(Player player) {
        Optional<PlayerOperationReceiptService.Receipt> receipt = receipts.claim(player);
        if (receipt.isEmpty()) {
            abortUnappliedClaims(player);
            return;
        }
        ClaimJournalRecord record = pendingClaims.get(receipt.get().operationToken());
        if (record == null) {
            ClaimJournalRecord prepared = unresolvedClaims.remove(receipt.get().operationToken());
            if (prepared != null && prepared.graveId().equals(receipt.get().graveId())) {
                record = prepared.withState(ClaimJournalState.PLAYER_APPLIED);
                try {
                    journal.writeClaim(record);
                } catch (IOException exception) {
                    unresolvedClaims.put(prepared.operationToken(), prepared);
                    feature.getLogger().log(Level.SEVERE, "Could not promote recovered claim journal.", exception);
                    return;
                }
                pendingClaims.put(record.operationToken(), record);
            }
        }
        if (record == null || !record.graveId().equals(receipt.get().graveId())) {
            receipts.clearClaim(player);
            player.saveData();
            return;
        }
        retryPendingClaim(record, player);
        abortUnappliedClaims(player);
    }

    private void abortUnappliedClaims(Player player) {
        for (ClaimJournalRecord unresolved : List.copyOf(unresolvedClaims.values())) {
            if (!unresolved.ownerUuid().equals(player.getUniqueId())) {
                continue;
            }
            unresolvedClaims.remove(unresolved.operationToken());
            repository.releaseOperation(unresolved.graveId(), unresolved.operationToken());
            try {
                journal.writeClaim(unresolved.withState(ClaimJournalState.ABORTED));
                journal.deleteClaim(unresolved.operationToken());
            } catch (IOException exception) {
                feature.getLogger().warning("Could not clean unapplied claim journal "
                        + unresolved.operationToken());
            }
        }
    }

    private void retryPendingClaimsForOnlineOwners() {
        for (ClaimJournalRecord record : List.copyOf(pendingClaims.values())) {
            Player owner = Bukkit.getPlayer(record.ownerUuid());
            if (owner != null && owner.isOnline()) {
                retryPendingClaim(record, owner);
            }
        }
    }

    private void retryPendingClaim(ClaimJournalRecord record, Player owner) {
        Grave grave = graves.get(record.graveId());
        if (grave == null || activeOperations.contains(grave.graveId())) {
            return;
        }
        Optional<PlayerOperationReceiptService.Receipt> receipt = receipts.claim(owner);
        if (receipt.isEmpty() || !receipt.get().operationToken().equals(record.operationToken())) {
            pendingClaims.remove(record.operationToken());
            repository.releaseOperation(record.graveId(), record.operationToken());
            try {
                journal.writeClaim(record.withState(ClaimJournalState.ABORTED));
                journal.deleteClaim(record.operationToken());
            } catch (IOException exception) {
                feature.getLogger().warning("Could not clean unapplied claim journal " + record.operationToken());
            }
            return;
        }
        if (!activeOperations.add(grave.graveId())) {
            return;
        }
        repository.loadPayload(grave.graveId()).whenComplete((currentEncoded, failure) -> {
            if (failure != null || currentEncoded.isEmpty()) {
                activeOperations.remove(grave.graveId());
                return;
            }
            try {
                GravePayload previous = payloadCodec.decode(
                        currentEncoded.get().bytes(),
                        currentEncoded.get().checksum()
                );
                GravePayload remaining = payloadCodec.decode(
                        record.remainingPayload().bytes(),
                        record.remainingPayload().checksum()
                );
                GraveStatus finalStatus = remaining.isEmpty() ? GraveStatus.CLAIMED : GraveStatus.PARTIAL;
                repository.finalizeClaim(
                        grave,
                        record.operationToken(),
                        record.actorUuid(),
                        previous,
                        remaining,
                        record.remainingPayload(),
                        finalStatus,
                        record.transferredEntries(),
                        record.transferredExperience()
                ).whenComplete((success, finalizeFailure) -> runMain(() -> {
                    activeOperations.remove(grave.graveId());
                    if (finalizeFailure != null || !Boolean.TRUE.equals(success)) {
                        return;
                    }
                    ClaimTransferPlan recoveredPlan = new ClaimTransferPlan(
                            PlayerInventoryState.capture(owner),
                            remaining,
                            record.transferredEntries(),
                            record.transferredExperience(),
                            true
                    );
                    completeFinalizedClaim(
                            grave,
                            owner,
                            remaining,
                            record,
                            finalStatus,
                            new CompletableFuture<>(),
                            ClaimReason.PENDING_DELIVERY
                    );
                }));
            } catch (IOException exception) {
                activeOperations.remove(grave.graveId());
                feature.getLogger().log(Level.SEVERE, "Could not decode pending claim recovery.", exception);
            }
        });
    }

    private void reconcileNearby(Grave grave) {
        grave.location().resolve().ifPresent(location -> {
            double radiusSquared = settings.despawnDistance() * settings.despawnDistance();
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                    reconcileViewer(player);
                }
            }
        });
    }

    private void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            onPlayerJoin(player);
        }
    }

    private boolean validatePhysicalInteraction(Player player, Grave grave) {
        Optional<Location> location = grave.location().resolve();
        if (location.isEmpty()
                || !grave.status().isPlayerClaimable()
                || !player.getWorld().equals(location.get().getWorld())
                || player.getLocation().distanceSquared(location.get())
                > settings.interactionDistance() * settings.interactionDistance()) {
            return false;
        }
        return !settings.requireLineOfSight() || player.hasLineOfSight(location.get().clone().add(0.0, 0.8, 0.0));
    }

    private int glowColor(Grave grave, Player viewer) {
        if (grave.ownerUuid().equals(viewer.getUniqueId())) {
            return settings.ownerGlowRgb();
        }
        if (viewer.hasPermission(INSPECT_PERMISSION)) {
            return settings.staffGlowRgb();
        }
        return settings.otherGlowRgb();
    }

    private String timerText(Grave grave) {
        long remaining = grave.remainingActiveMillis(feature.getPlugin().getServerActiveClock().nowMillis());
        if (grave.status() == GraveStatus.DELIVERY_PENDING) {
            return "Delivery pending";
        }
        if (grave.status() == GraveStatus.ORPHANED_WORLD) {
            return "Remote recovery available";
        }
        return "Disappears in " + formatDuration(remaining);
    }

    private void updateTracking(Player player) {
        UUID graveId = trackedGraves.get(player.getUniqueId());
        if (graveId == null) {
            return;
        }
        Grave grave = graves.get(graveId);
        if (grave == null || !grave.status().hasRecoverablePayload()) {
            trackedGraves.remove(player.getUniqueId());
            return;
        }
        Optional<Location> location = grave.location().resolve();
        if (location.isEmpty() || !player.getWorld().equals(location.get().getWorld())) {
            player.sendActionBar(Component.text(
                    "Grave " + grave.shortId() + " · " + grave.location().worldKey()
                            + " · " + timerText(grave),
                    NamedTextColor.AQUA
            ));
            return;
        }
        int distance = (int) Math.round(player.getLocation().distance(location.get()));
        player.sendActionBar(Component.text(
                "Grave " + grave.shortId() + " · " + distance + "m · " + timerText(grave),
                NamedTextColor.AQUA
        ));
    }

    private void playEffect(Grave grave, Particle particle, Sound sound, int count) {
        grave.location().resolve().ifPresent(location -> {
            location.getWorld().spawnParticle(
                    particle,
                    location.clone().add(0.0, 0.8, 0.0),
                    count,
                    0.6,
                    0.8,
                    0.6,
                    0.02
            );
            location.getWorld().playSound(location, sound, 0.7f, 1.0f);
        });
    }

    private int visiblePairCount() {
        int count = 0;
        for (Map<UUID, GraveViewerState> states : viewerStates.values()) {
            count += (int) states.values().stream().filter(GraveViewerState::spawned).count();
        }
        return count;
    }

    private double distanceSquared(Player player, Grave grave) {
        return grave.location().resolve()
                .filter(location -> location.getWorld().equals(player.getWorld()))
                .map(location -> player.getLocation().distanceSquared(location))
                .orElse(Double.MAX_VALUE);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1_000L);
        if (seconds >= 3_600L) {
            long roundedMinutes = (seconds + 59L) / 60L;
            return roundedMinutes / 60L + "h " + roundedMinutes % 60L + "m";
        }
        if (seconds >= 300L) {
            seconds = ((seconds + 9L) / 10L) * 10L;
        }
        return seconds / 60L + "m " + seconds % 60L + "s";
    }

    private static String normalizeShortId(String shortId) {
        return shortId == null ? "" : shortId.trim().toUpperCase(Locale.ROOT);
    }

    private void runMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else if (!shuttingDown.get()) {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(action);
        }
    }

    private record InteractionHandle(UUID graveId, long generation) {
    }
}
