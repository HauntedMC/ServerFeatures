package nl.hauntedmc.serverfeatures.features.nametags.internal;

import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.nametags.Nametags;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.NametagAttachmentIndex;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.NametagUpdater;
import nl.hauntedmc.serverfeatures.features.nametags.internal.update.UpdateProperties;
import nl.hauntedmc.serverfeatures.features.nametags.internal.visibility.VisibilityManager;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureTaskManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main-thread owner of the complete nametag lifecycle.
 *
 * <p>Tracking events drive normal visibility. A periodic reconciliation pass recovers from missed
 * third-party events, while generation tokens and cancellable tasks prevent stale delayed spawns
 * from creating detached client-side entities after an untrack, teleport, relog, or rebuild.</p>
 */
public final class NametagManager {
    private final NametagRegistry registry = new NametagRegistry();
    private final NametagUpdater updater = new NametagUpdater();
    private final NametagAttachmentIndex attachmentIndex = new NametagAttachmentIndex();
    private final Nametags feature;
    private final FeatureTaskManager taskManager;
    private final VisibilityManager visibilityManager;

    private final int joinSettleDelayTicks;
    private final int trackingSettleDelayTicks;
    private final int transitionSettleDelayTicks;
    private final int reconcileIntervalTicks;
    private final boolean remountRepairEnabled;
    private final int remountRepairIntervalTicks;
    private final double teleportRebuildDistanceSquared;

    private final AtomicLong tokenSequence = new AtomicLong();
    private final Map<UUID, Boolean> selfViewPreference = new ConcurrentHashMap<>();
    private final Map<UUID, Long> selfViewLoadGeneration = new ConcurrentHashMap<>();
    private final Map<UUID, Long> connectionGeneration = new ConcurrentHashMap<>();
    private final Set<UUID> glideSuppressed = ConcurrentHashMap.newKeySet();

    // Main-thread-only transition state.
    private final Map<UUID, Long> transitionGeneration = new HashMap<>();
    private final Map<UUID, BukkitTask> transitionTasks = new HashMap<>();
    private final Set<UUID> suspendedOwners = new HashSet<>();

    private NametagManager(Nametags feature) {
        this.feature = feature;
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        this.visibilityManager = new VisibilityManager(feature);

        this.joinSettleDelayTicks = intConfig("lifecycle.join_settle_delay_ticks", 10, 1);
        this.trackingSettleDelayTicks = intConfig("lifecycle.tracking_settle_delay_ticks", 2, 1);
        this.transitionSettleDelayTicks = intConfig("lifecycle.transition_settle_delay_ticks", 10, 1);
        this.reconcileIntervalTicks = intConfig("reconciliation.interval_ticks", 10, 1);
        this.remountRepairEnabled = booleanConfig("repair.remount_enabled", true);
        this.remountRepairIntervalTicks = intConfig("repair.remount_interval_ticks", 100, 20);
        int teleportDistance = intConfig("lifecycle.teleport_rebuild_distance", 64, 1);
        this.teleportRebuildDistanceSquared = (double) teleportDistance * teleportDistance;
    }

    public static NametagManager create(Nametags feature) {
        NametagManager manager = new NametagManager(feature);
        manager.scheduleReconciliation();
        manager.scheduleRemountRepair();
        return manager;
    }

    public NametagAttachmentIndex getAttachmentIndex() {
        return attachmentIndex;
    }

    public VisibilityManager getVisibilityManager() {
        return visibilityManager;
    }

    public int getViewerUpdateDelayTicks() {
        return trackingSettleDelayTicks;
    }

    public void handleJoin(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long session = beginConnectionSession(playerId);
        preloadSelfView(player, () -> scheduleRegistration(player, session));
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        invalidateConnectionSession(playerId);
        invalidateSelfViewLoad(playerId);
        executeOnMain(() -> removePlayerNow(player));
    }

    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            handleJoin(player);
        }
    }

    public boolean isSelfViewEnabled(UUID playerId) {
        return selfViewPreference.getOrDefault(playerId, true);
    }

    public boolean isSelfViewEnabled(Player player) {
        return isSelfViewEnabled(player.getUniqueId());
    }

    public Boolean getCachedSelfViewPreference(Player player) {
        return selfViewPreference.get(player.getUniqueId());
    }

    public void setSelfViewEnabled(UUID playerId, boolean enabled) {
        invalidateSelfViewLoad(playerId);
        selfViewPreference.put(playerId, enabled);

        executeOnMain(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }

            reconcilePair(player, player, 1, true);
            String playerName = player.getName();
            feature.getRepository()
                    .upsertSelfView(playerId.toString(), playerName, enabled)
                    .exceptionally(ex -> {
                        feature.getLogger().warning(
                                "Kon selfview status niet opslaan voor " + playerName + ": " + rootMessage(ex)
                        );
                        return null;
                    });
        });
    }

    public void setSelfViewEnabled(Player player, boolean enabled) {
        setSelfViewEnabled(player.getUniqueId(), enabled);
    }

    public boolean isSelfViewAllowedNow(UUID playerId) {
        return selfViewPreference.getOrDefault(playerId, true) && !glideSuppressed.contains(playerId);
    }

    public boolean isSelfViewAllowedNow(Player player) {
        return isSelfViewAllowedNow(player.getUniqueId());
    }

    public void setGlideSuppressed(Player player, boolean suppressed) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (suppressed) {
            glideSuppressed.add(playerId);
        } else {
            glideSuppressed.remove(playerId);
        }
        executeOnMain(() -> reconcilePair(player, player, 1, true));
    }

    public void preloadSelfView(Player player) {
        handleJoin(player);
    }

    public void preloadSelfView(Player player, Runnable afterLoad) {
        if (player == null) {
            return;
        }
        Objects.requireNonNull(afterLoad, "afterLoad must not be null");

        UUID playerId = player.getUniqueId();
        Long existingSession = connectionGeneration.get(playerId);
        long session = existingSession == null ? beginConnectionSession(playerId) : existingSession;
        long loadGeneration = beginSelfViewLoad(playerId);
        String playerName = player.getName();

        try {
            feature.getRepository()
                    .findSelfView(playerId.toString())
                    .whenComplete((persisted, throwable) -> {
                        if (isCurrentSelfViewLoad(playerId, loadGeneration)) {
                            if (throwable != null) {
                                feature.getLogger().warning(
                                        "Kon selfview voorkeur niet laden voor " + playerName + ": "
                                                + rootMessage(throwable)
                                );
                                selfViewPreference.putIfAbsent(playerId, true);
                            } else {
                                selfViewPreference.put(
                                        playerId,
                                        persisted == null ? true : persisted.orElse(true)
                                );
                            }
                        }
                        runAfterSelfViewLoaded(player, playerId, session, afterLoad);
                    });
        } catch (RuntimeException exception) {
            if (isCurrentSelfViewLoad(playerId, loadGeneration)) {
                selfViewPreference.putIfAbsent(playerId, true);
            }
            feature.getLogger().warning(
                    "Kon selfview voorkeur niet laden voor " + playerName + ": " + rootMessage(exception)
            );
            runAfterSelfViewLoaded(player, playerId, session, afterLoad);
        }
    }

    public void onViewerTracks(Player viewer, Player owner) {
        executeOnMain(() -> reconcilePair(viewer, owner, trackingSettleDelayTicks, false));
    }

    public void onViewerUntracks(Player viewer, Player owner) {
        if (viewer == null || owner == null) {
            return;
        }
        taskManager.scheduleDelayedTask(
                () -> {
                    Nametag nametag = registry.getNametag(owner.getUniqueId()).orElse(null);
                    if (nametag == null) {
                        return;
                    }

                    boolean trackedAgain = viewer.isOnline()
                            && owner.isOnline()
                            && owner.getTrackedBy().contains(viewer);
                    boolean currentAttachmentStillVisible = attachmentIndex.isVisible(
                            nametag.getOwnerEntityId(),
                            nametag.getEntityId(),
                            viewer.getUniqueId()
                    );

                    // Ignore a late untrack from an older owner generation when the current fake is intact.
                    if (trackedAgain && currentAttachmentStillVisible) {
                        return;
                    }

                    hideFromViewer(nametag, viewer, true);
                    if (trackedAgain) {
                        reconcilePair(viewer, owner, trackingSettleDelayTicks, false);
                    }
                },
                BukkitTime.ticks(1L)
        );
    }

    public boolean requiresTeleportRebuild(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()) {
            return true;
        }
        return from.distanceSquared(to) >= teleportRebuildDistanceSquared;
    }

    public void beginPlayerTransition(Player player) {
        beginPlayerTransition(player, transitionSettleDelayTicks);
    }

    public void beginPlayerTransition(Player player, int delayTicks) {
        executeOnMain(() -> beginPlayerTransitionNow(player, Math.max(1, delayTicks)));
    }

    public void suspendForDeath(Player player) {
        executeOnMain(() -> {
            if (player == null) {
                return;
            }
            cancelTransition(player.getUniqueId());
            suspendOwnedNametag(player);
            removeViewerFromAll(player, true);
        });
    }

    public void handleRespawn(Player player) {
        beginPlayerTransition(player, transitionSettleDelayTicks);
    }

    public void rebuildOwner(Player player, int delayTicks, boolean refreshText) {
        executeOnMain(() -> rebuildOwnerNow(player, Math.max(1, delayTicks), refreshText, false));
    }

    public void handlePassengerMutation(Player player) {
        if (player == null) {
            return;
        }
        taskManager.scheduleDelayedTask(
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Nametag nametag = registry.getNametag(player.getUniqueId()).orElse(null);
                    if (nametag == null || suspendedOwners.contains(player.getUniqueId())) {
                        return;
                    }
                    remountVisibleViewers(nametag);
                },
                BukkitTime.ticks(1L)
        );
    }

    public void handleGameModeChange(Player player) {
        if (player == null) {
            return;
        }
        taskManager.scheduleDelayedTask(
                () -> {
                    if (player.isOnline()) {
                        reconcileOwner(player, 1);
                        reconcileViewer(player, 1);
                    }
                },
                BukkitTime.ticks(1L)
        );
    }

    public void refreshText(UUID playerId, int delayTicks) {
        scheduleOnMain(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                refreshTextNow(player);
            }
        }, Math.max(0, delayTicks));
    }

    public void refreshViewer(Player viewer) {
        executeOnMain(() -> {
            if (viewer == null || !viewer.isOnline()) {
                return;
            }
            for (Nametag nametag : snapshotNametags()) {
                hideFromViewer(nametag, viewer, true);
                if (shouldShow(viewer, nametag)) {
                    ensureShown(nametag, viewer, trackingSettleDelayTicks);
                }
            }
        });
    }

    /**
     * Compatibility entry point used by existing hooks while routing all work through the new lifecycle.
     */
    public void updateNametag(Player player, UpdateProperties properties) {
        if (player == null || properties == null) {
            return;
        }

        executeOnMain(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (properties.getUpdateText()) {
                refreshTextNow(player);
            }
            if (properties.isForced()) {
                rebuildOwnerNow(player, Math.max(1L, properties.getDelay()), false, false);
            } else if (properties.isOwnerOnly()) {
                reconcilePair(player, player, Math.max(1L, properties.getDelay()), true);
            } else {
                reconcileOwner(player, Math.max(1L, properties.getDelay()));
                reconcileViewer(player, Math.max(1L, properties.getDelay()));
            }
        });
    }

    public void updateNametag(int entityId, UpdateProperties properties) {
        executeOnMain(() -> registry.getNametagByEntityId(entityId)
                .ifPresent(nametag -> updateNametag(nametag.getNametagOwner(), properties)));
    }

    public void removeNametag(Player player) {
        handleQuit(player);
    }

    public void removeAllNametags() {
        executeOnMain(this::shutdownNow);
    }

    public List<Player> getRegisteredPlayers() {
        List<Player> players = new ArrayList<>();
        for (Nametag nametag : snapshotNametags()) {
            Player player = nametag.getNametagOwner();
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private void scheduleRegistration(Player player, long session) {
        taskManager.scheduleDelayedTask(
                () -> {
                    UUID playerId = player.getUniqueId();
                    if (!feature.getPlugin().isEnabled()
                            || !player.isOnline()
                            || !isCurrentConnectionSession(playerId, session)) {
                        return;
                    }
                    registerPlayerNow(player);
                },
                BukkitTime.ticks(joinSettleDelayTicks)
        );
    }

    private void registerPlayerNow(Player player) {
        UUID playerId = player.getUniqueId();
        registry.getNametag(playerId).ifPresent(this::removeOwnedNametagNow);

        Nametag nametag = new Nametag(player);
        registry.register(nametag);
        attachmentIndex.register(nametag.getOwnerEntityId(), nametag.getEntityId());

        reconcileOwner(nametag, trackingSettleDelayTicks);
        reconcileViewer(player, trackingSettleDelayTicks);
    }

    private void removePlayerNow(Player player) {
        UUID playerId = player.getUniqueId();
        cancelTransition(playerId);
        registry.getNametag(playerId).ifPresent(this::removeOwnedNametagNow);
        removeViewerFromAll(player, true);

        suspendedOwners.remove(playerId);
        glideSuppressed.remove(playerId);
        selfViewPreference.remove(playerId);
        selfViewLoadGeneration.remove(playerId);
        connectionGeneration.remove(playerId);
        transitionGeneration.remove(playerId);
    }

    private void removeOwnedNametagNow(Nametag nametag) {
        registry.unregister(nametag);
        suspendedOwners.remove(nametag.getNametagOwnerId());
        cancelAllViewerTasks(nametag);
        attachmentIndex.unregister(nametag.getOwnerEntityId(), nametag.getEntityId());

        // A destroy packet for an unknown id is harmless and removes ghosts even if bookkeeping was stale.
        updater.destroy(nametag.getEntityId(), Bukkit.getOnlinePlayers());
        nametag.clearViewerStates();
    }

    private void beginPlayerTransitionNow(Player player, int delayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long generation = nextToken();
        transitionGeneration.put(playerId, generation);
        cancelTransitionTask(playerId);
        suspendOwnedNametag(player);
        removeViewerFromAll(player, true);

        BukkitTask task = taskManager.scheduleDelayedTask(
                () -> completePlayerTransition(player, generation),
                BukkitTime.ticks(delayTicks)
        );
        transitionTasks.put(playerId, task);
    }

    private void completePlayerTransition(Player player, long generation) {
        UUID playerId = player.getUniqueId();
        transitionTasks.remove(playerId);
        if (!player.isOnline()
                || transitionGeneration.getOrDefault(playerId, 0L) != generation) {
            return;
        }

        Nametag nametag = registry.getNametag(playerId).orElse(null);
        if (nametag == null) {
            transitionGeneration.remove(playerId, generation);
            suspendedOwners.remove(playerId);
            registerPlayerNow(player);
            return;
        }

        transitionGeneration.remove(playerId, generation);
        nametag.rotateEntityIdentity();
        attachmentIndex.register(nametag.getOwnerEntityId(), nametag.getEntityId());
        suspendedOwners.remove(playerId);

        reconcileOwner(nametag, trackingSettleDelayTicks);
        reconcileViewer(player, trackingSettleDelayTicks);
    }

    private void rebuildOwnerNow(Player player, long delayTicks, boolean refreshText, boolean restoreViewer) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long generation = nextToken();
        transitionGeneration.put(playerId, generation);
        cancelTransitionTask(playerId);
        suspendOwnedNametag(player);

        BukkitTask task = taskManager.scheduleDelayedTask(
                () -> {
                    transitionTasks.remove(playerId);
                    if (!player.isOnline()
                            || transitionGeneration.getOrDefault(playerId, 0L) != generation) {
                        return;
                    }

                    Nametag nametag = registry.getNametag(playerId).orElse(null);
                    if (nametag == null) {
                        transitionGeneration.remove(playerId, generation);
                        suspendedOwners.remove(playerId);
                        registerPlayerNow(player);
                        return;
                    }
                    transitionGeneration.remove(playerId, generation);
                    if (refreshText) {
                        nametag.updateNametagText();
                    }
                    nametag.rotateEntityIdentity();
                    attachmentIndex.register(nametag.getOwnerEntityId(), nametag.getEntityId());
                    suspendedOwners.remove(playerId);
                    reconcileOwner(nametag, trackingSettleDelayTicks);
                    if (restoreViewer) {
                        reconcileViewer(player, trackingSettleDelayTicks);
                    }
                },
                BukkitTime.ticks(delayTicks)
        );
        transitionTasks.put(playerId, task);
    }

    private void suspendOwnedNametag(Player player) {
        Nametag nametag = registry.getNametag(player.getUniqueId()).orElse(null);
        if (nametag == null) {
            return;
        }

        suspendedOwners.add(player.getUniqueId());
        cancelAllViewerTasks(nametag);
        attachmentIndex.unregister(nametag.getOwnerEntityId(), nametag.getEntityId());
        updater.destroy(nametag.getEntityId(), Bukkit.getOnlinePlayers());
        nametag.clearViewerStates();
    }

    private void reconcileAll() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Nametag reconciliation must run on the Bukkit main thread.");
        }

        for (Nametag nametag : snapshotNametags()) {
            Player owner = nametag.getNametagOwner();
            if (owner == null || !owner.isOnline()) {
                removeOwnedNametagNow(nametag);
                continue;
            }
            reconcileOwner(nametag, trackingSettleDelayTicks);
        }
    }

    private void reconcileOwner(Player owner, long delayTicks) {
        registry.getNametag(owner.getUniqueId()).ifPresent(nametag ->
                reconcileOwner(nametag, delayTicks)
        );
    }

    private void reconcileOwner(Nametag nametag, long delayTicks) {
        Player owner = nametag.getNametagOwner();
        if (owner == null || !owner.isOnline() || suspendedOwners.contains(nametag.getNametagOwnerId())) {
            return;
        }

        Set<UUID> candidates = new LinkedHashSet<>();
        for (Player viewer : owner.getTrackedBy()) {
            candidates.add(viewer.getUniqueId());
            reconcilePair(viewer, owner, delayTicks, false);
        }
        candidates.add(owner.getUniqueId());
        reconcilePair(owner, owner, delayTicks, false);

        for (Map.Entry<UUID, NametagViewerState> entry : nametag.snapshotViewerStates().entrySet()) {
            if (candidates.contains(entry.getKey())) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && viewer.isOnline()) {
                hideFromViewer(nametag, viewer, true);
            } else {
                invalidateViewerState(nametag, entry.getKey(), entry.getValue());
            }
        }
    }

    private void reconcileViewer(Player viewer, long delayTicks) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        for (Nametag nametag : snapshotNametags()) {
            if (shouldShow(viewer, nametag)) {
                ensureShown(nametag, viewer, delayTicks);
            } else {
                hideFromViewer(nametag, viewer, false);
            }
        }
    }

    private void reconcilePair(
            Player viewer,
            Player owner,
            long delayTicks,
            boolean aggressiveHide
    ) {
        if (viewer == null || owner == null) {
            return;
        }
        Nametag nametag = registry.getNametag(owner.getUniqueId()).orElse(null);
        if (nametag == null) {
            return;
        }

        if (shouldShow(viewer, nametag)) {
            ensureShown(nametag, viewer, delayTicks);
        } else {
            hideFromViewer(nametag, viewer, aggressiveHide);
        }
    }

    private boolean shouldShow(Player viewer, Nametag nametag) {
        Player owner = nametag.getNametagOwner();
        if (viewer == null
                || owner == null
                || !viewer.isOnline()
                || !owner.isOnline()
                || viewer.isDead()
                || owner.isDead()
                || suspendedOwners.contains(nametag.getNametagOwnerId())
                || viewer.getWorld() != owner.getWorld()) {
            return false;
        }

        boolean selfView = viewer.getUniqueId().equals(nametag.getNametagOwnerId());
        if (selfView) {
            if (!isSelfViewAllowedNow(viewer)) {
                return false;
            }
        } else if (!owner.getTrackedBy().contains(viewer)) {
            return false;
        }

        return visibilityManager.isPlayerVisible(viewer, nametag);
    }

    private void ensureShown(Nametag nametag, Player viewer, long delayTicks) {
        UUID viewerId = viewer.getUniqueId();
        NametagViewerState state = nametag.getOrCreateViewerState(viewerId);
        if (state.isSpawned()) {
            if (attachmentIndex.isVisible(
                    nametag.getOwnerEntityId(),
                    nametag.getEntityId(),
                    viewerId
            )) {
                return;
            }
            invalidateViewerState(nametag, viewerId, state);
            state = nametag.getOrCreateViewerState(viewerId);
        }
        if (state.hasPendingSpawn()) {
            return;
        }

        long viewerGeneration = state.nextGeneration();
        long entityGeneration = nametag.getEntityGeneration();
        BukkitTask task = taskManager.scheduleDelayedTask(
                () -> completeSpawn(
                        nametag,
                        viewerId,
                        state,
                        viewerGeneration,
                        entityGeneration
                ),
                BukkitTime.ticks(Math.max(1L, delayTicks))
        );
        BukkitTask previous = state.replacePendingSpawn(task);
        if (previous != null) {
            taskManager.cancelTask(previous);
        }
    }

    private void completeSpawn(
            Nametag nametag,
            UUID viewerId,
            NametagViewerState expectedState,
            long viewerGeneration,
            long entityGeneration
    ) {
        expectedState.clearPendingSpawn();
        if (!expectedState.isCurrent(viewerGeneration)
                || nametag.getEntityGeneration() != entityGeneration
                || registry.getNametag(nametag.getNametagOwnerId()).orElse(null) != nametag
                || nametag.getViewerState(viewerId) != expectedState) {
            return;
        }

        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !shouldShow(viewer, nametag)) {
            nametag.removeViewerState(viewerId, expectedState);
            return;
        }

        try {
            updater.spawn(nametag, viewer);
            expectedState.markSpawned();
            attachmentIndex.markVisible(nametag.getOwnerEntityId(), viewerId);
        } catch (RuntimeException exception) {
            attachmentIndex.markHidden(nametag.getOwnerEntityId(), viewerId);
            expectedState.markHidden();
            nametag.removeViewerState(viewerId, expectedState);
            feature.getLogger().warning(
                    "Kon nametag niet tonen aan " + viewer.getName() + ": " + rootMessage(exception)
            );
        }
    }

    private void hideFromViewer(Nametag nametag, Player viewer, boolean aggressive) {
        UUID viewerId = viewer.getUniqueId();
        NametagViewerState state = nametag.getViewerState(viewerId);
        boolean wasSpawned = state != null && state.isSpawned();

        if (state != null) {
            invalidateViewerState(nametag, viewerId, state);
        }
        attachmentIndex.markHidden(nametag.getOwnerEntityId(), viewerId);

        if (aggressive || wasSpawned) {
            updater.destroy(nametag.getEntityId(), viewer);
        }
    }

    private void invalidateViewerState(
            Nametag nametag,
            UUID viewerId,
            NametagViewerState state
    ) {
        state.nextGeneration();
        BukkitTask pending = state.clearPendingSpawn();
        if (pending != null) {
            taskManager.cancelTask(pending);
        }
        state.markHidden();
        attachmentIndex.markHidden(nametag.getOwnerEntityId(), viewerId);
        nametag.removeViewerState(viewerId, state);
    }

    private void cancelAllViewerTasks(Nametag nametag) {
        for (Map.Entry<UUID, NametagViewerState> entry : nametag.snapshotViewerStates().entrySet()) {
            invalidateViewerState(nametag, entry.getKey(), entry.getValue());
        }
    }

    private void removeViewerFromAll(Player viewer, boolean aggressive) {
        for (Nametag nametag : snapshotNametags()) {
            hideFromViewer(nametag, viewer, aggressive);
        }
    }

    private void remountVisibleViewers(Nametag nametag) {
        for (Map.Entry<UUID, NametagViewerState> entry : nametag.snapshotViewerStates().entrySet()) {
            if (!entry.getValue().isSpawned()) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !shouldShow(viewer, nametag)) {
                if (viewer != null && viewer.isOnline()) {
                    hideFromViewer(nametag, viewer, true);
                } else {
                    invalidateViewerState(nametag, entry.getKey(), entry.getValue());
                }
                continue;
            }
            updater.remount(nametag, viewer);
        }
    }

    private void refreshTextNow(Player player) {
        Nametag nametag = registry.getNametag(player.getUniqueId()).orElse(null);
        if (nametag == null || suspendedOwners.contains(player.getUniqueId())) {
            return;
        }

        nametag.updateNametagText();
        List<Player> visibleViewers = new ArrayList<>();
        for (Map.Entry<UUID, NametagViewerState> entry : nametag.snapshotViewerStates().entrySet()) {
            if (!entry.getValue().isSpawned()) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && shouldShow(viewer, nametag)) {
                visibleViewers.add(viewer);
            }
        }
        updater.updateMetadata(nametag, visibleViewers);
    }

    private void scheduleReconciliation() {
        taskManager.scheduleRepeatingTask(
                this::reconcileAll,
                BukkitTime.ticks(reconcileIntervalTicks),
                BukkitTime.ticks(reconcileIntervalTicks)
        );
    }

    private void scheduleRemountRepair() {
        if (!remountRepairEnabled) {
            return;
        }
        taskManager.scheduleRepeatingTask(
                () -> {
                    for (Nametag nametag : snapshotNametags()) {
                        if (!suspendedOwners.contains(nametag.getNametagOwnerId())) {
                            remountVisibleViewers(nametag);
                        }
                    }
                },
                BukkitTime.ticks(remountRepairIntervalTicks),
                BukkitTime.ticks(remountRepairIntervalTicks)
        );
    }

    private void shutdownNow() {
        for (BukkitTask transitionTask : List.copyOf(transitionTasks.values())) {
            taskManager.cancelTask(transitionTask);
        }
        transitionTasks.clear();
        transitionGeneration.clear();

        for (Nametag nametag : snapshotNametags()) {
            removeOwnedNametagNow(nametag);
        }

        attachmentIndex.clear();
        suspendedOwners.clear();
        glideSuppressed.clear();
        selfViewPreference.clear();
        selfViewLoadGeneration.clear();
        connectionGeneration.clear();
    }

    private Collection<Nametag> snapshotNametags() {
        return List.copyOf(registry.getAllNametags());
    }

    private void executeOnMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            taskManager.scheduleOneTimeTask(action);
        }
    }

    private void scheduleOnMain(Runnable action, int delayTicks) {
        if (delayTicks <= 0 && Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        taskManager.scheduleDelayedTask(action, BukkitTime.ticks(Math.max(0, delayTicks)));
    }

    private long beginConnectionSession(UUID playerId) {
        long token = nextToken();
        connectionGeneration.put(playerId, token);
        return token;
    }

    private void invalidateConnectionSession(UUID playerId) {
        connectionGeneration.put(playerId, nextToken());
    }

    private boolean isCurrentConnectionSession(UUID playerId, long session) {
        return connectionGeneration.getOrDefault(playerId, 0L) == session;
    }

    private long beginSelfViewLoad(UUID playerId) {
        long token = nextToken();
        selfViewLoadGeneration.put(playerId, token);
        return token;
    }

    private void invalidateSelfViewLoad(UUID playerId) {
        selfViewLoadGeneration.put(playerId, nextToken());
    }

    private boolean isCurrentSelfViewLoad(UUID playerId, long generation) {
        return selfViewLoadGeneration.getOrDefault(playerId, 0L) == generation;
    }

    private void runAfterSelfViewLoaded(
            Player player,
            UUID playerId,
            long session,
            Runnable afterLoad
    ) {
        try {
            taskManager.scheduleOneTimeTask(() -> {
                if (!feature.getPlugin().isEnabled()
                        || !player.isOnline()
                        || !player.getUniqueId().equals(playerId)
                        || !isCurrentConnectionSession(playerId, session)) {
                    return;
                }
                afterLoad.run();
            });
        } catch (RuntimeException exception) {
            if (feature.getPlugin().isEnabled()) {
                feature.getLogger().warning(
                        "Kon nametag initialisatie niet plannen voor " + player.getName() + ": "
                                + rootMessage(exception)
                );
            }
        }
    }

    private void cancelTransition(UUID playerId) {
        transitionGeneration.put(playerId, nextToken());
        cancelTransitionTask(playerId);
    }

    private void cancelTransitionTask(UUID playerId) {
        BukkitTask existing = transitionTasks.remove(playerId);
        if (existing != null) {
            taskManager.cancelTask(existing);
        }
    }

    private long nextToken() {
        return tokenSequence.incrementAndGet();
    }

    private int intConfig(String path, int fallback, int minimum) {
        Object raw = feature.getConfigHandler().get(path);
        if (raw instanceof Number number) {
            return Math.max(minimum, number.intValue());
        }
        return Math.max(minimum, fallback);
    }

    private boolean booleanConfig(String path, boolean fallback) {
        Object raw = feature.getConfigHandler().get(path);
        return raw instanceof Boolean value ? value : fallback;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
