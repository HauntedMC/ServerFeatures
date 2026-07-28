package nl.hauntedmc.serverfeatures.features.invtools.service;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InventoryClickMutation;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InventorySlotLayout;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.NbtOfflinePlayerDataStore;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.OfflinePlayerData;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.OfflinePlayerDataStore;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataConflictException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class InvToolsService {

    private static final String PERMISSION_PREFIX = "serverfeatures.feature.invtools.command.";

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int TRANSITION_LOCK_COUNT = 64;
    private static final int MAX_LOGIN_BARRIER_SECONDS = 30;

    private final InvTools feature;
    private final OfflinePlayerDataStore offlineStore;
    private final Duration loginBarrierTimeout;
    private final boolean auditEdits;
    private final Object[] transitionLocks = createTransitionLocks();

    private final Map<UUID, InvToolsView> viewsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<InvToolsView>> viewsByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> editorsByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, OfflineAccess> offlineAccesses = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> latestOpenRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    public InvToolsService(InvTools feature) {
        this(
                feature,
                new NbtOfflinePlayerDataStore(feature.getPlugin().getServer().getLevelDirectory()),
                Duration.ofSeconds(Math.clamp(feature.getConfigHandler().get(
                        "offline_io_timeout_seconds",
                        Integer.class,
                        10
                ), 1, MAX_LOGIN_BARRIER_SECONDS)),
                feature.getConfigHandler().get("audit_edits", Boolean.class, true)
        );
    }

    InvToolsService(
            InvTools feature,
            OfflinePlayerDataStore offlineStore,
            Duration loginBarrierTimeout,
            boolean auditEdits
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.offlineStore = Objects.requireNonNull(offlineStore, "offlineStore");
        this.loginBarrierTimeout = Objects.requireNonNull(loginBarrierTimeout, "loginBarrierTimeout");
        this.auditEdits = auditEdits;
    }

    public void open(Player viewer, String requestedName, InventoryKind kind) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kind, "kind");
        if (!active.get() || !viewer.isOnline() || !hasInspectPermission(viewer, kind)) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        UUID requestId = UUID.randomUUID();
        latestOpenRequests.put(viewerId, requestId);
        cancelPendingReservations(viewerId);

        String name = requestedName == null ? "" : requestedName.trim();
        if (!PLAYER_NAME.matcher(name).matches()) {
            latestOpenRequests.remove(viewerId, requestId);
            send(viewer, "invtools.invalid_name", "player", name);
            return;
        }

        Player onlineTarget = Bukkit.getPlayerExact(name);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            latestOpenRequests.remove(viewerId, requestId);
            openOnline(viewer, onlineTarget, kind);
            return;
        }

        OfflinePlayer localPlayer = Bukkit.getOfflinePlayerIfCached(name);
        if (localPlayer == null) {
            latestOpenRequests.remove(viewerId, requestId);
            send(viewer, "invtools.not_played_here", "player", name);
            return;
        }
        send(viewer, "invtools.loading", "player", name);
        String localName = localPlayer.getName();
        openResolved(
                viewer,
                localPlayer.getUniqueId(),
                localName == null ? name : localName,
                kind,
                requestId
        );
    }

    public void refreshOnlineViews() {
        if (!active.get()) {
            return;
        }
        for (InvToolsView view : ListCopy.of(viewsByViewer.values())) {
            Player viewer = Bukkit.getPlayer(view.viewerId());
            if (viewer == null || !viewer.isOnline()) {
                closeView(view);
                continue;
            }
            if (!hasInspectPermission(viewer, view.kind())) {
                closeView(view);
                closeIfViewing(viewer, view);
                send(viewer, "invtools.permission_revoked");
            } else if (view.editable() && !hasEditPermission(viewer, view.kind())) {
                closeView(view);
                closeIfViewing(viewer, view);
                send(viewer, "invtools.edit_permission_revoked");
            }
        }

        for (Map.Entry<UUID, Set<InvToolsView>> entry : viewsByTarget.entrySet()) {
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null || !target.isOnline()) {
                closeOnlineTargetViews(entry.getKey());
                continue;
            }
            InventorySnapshot snapshot = InventorySnapshot.capture(target);
            for (InvToolsView view : ListCopy.of(entry.getValue())) {
                if (view.onlineSession()) {
                    view.refresh(snapshot);
                }
            }
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        InvToolsView view = holder(event.getView().getTopInventory());
        if (view == null || !view.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        if (!hasInspectPermission(viewer, view.kind())) {
            event.setCancelled(true);
            closeView(view);
            scheduleMain(() -> {
                closeIfViewing(viewer, view);
                send(viewer, "invtools.permission_revoked");
            });
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }
        if (!view.owns(event.getClickedInventory())) {
            if (view.isolatesViewerCursor() || canRouteThroughTop(event.getAction())) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        int guiSlot = event.getSlot();
        if (guiSlot == InventorySlotLayout.closeSlot(view.kind())) {
            view.freeze();
            scheduleMain(() -> closeIfViewing(viewer, view));
            return;
        }

        var backingSlot = InventorySlotLayout.backingSlot(view.kind(), guiSlot);
        if (backingSlot.isEmpty()) {
            return;
        }
        if (!view.isInteractive()) {
            return;
        }
        if (!view.editable() || !hasEditPermission(viewer, view.kind())) {
            if (view.editable()) {
                closeView(view);
                scheduleMain(() -> {
                    closeIfViewing(viewer, view);
                    send(viewer, "invtools.edit_permission_revoked");
                });
            } else {
                send(viewer, "invtools.read_only");
            }
            return;
        }

        ItemStack cursor = view.onlineSession() ? event.getCursor() : view.cursor();
        if (!view.onlineSession() && !sameItem(cursor, event.getCursor())) {
            viewer.setItemOnCursor(cursor);
            feature.getLogger().warning(
                    "Corrected an unexpected cursor change in offline InvTools view for "
                            + viewer.getUniqueId()
            );
            return;
        }

        ItemStack current = view.snapshot().itemAt(view.kind(), backingSlot.getAsInt());
        InventoryClickMutation.apply(event.getAction(), current, cursor)
                .ifPresent(result -> {
                    if (!allowsItemInSlot(view.kind(), backingSlot.getAsInt(), result.slotItem())) {
                        return;
                    }
                    if (!view.updateBackingSlot(
                            backingSlot.getAsInt(),
                            result.slotItem(),
                            result.cursorItem()
                    )) {
                        return;
                    }
                    viewer.setItemOnCursor(result.cursorItem());
                    applyMutation(view, backingSlot.getAsInt(), result.slotItem());
                    auditMutation(
                            viewer,
                            view,
                            backingSlot.getAsInt(),
                            current,
                            result.slotItem()
                    );
                });
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        InvToolsView view = holder(event.getInventory());
        if (view == null || !view.viewerId().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        if (view.isolatesViewerCursor()) {
            event.getView().setCursor(null);
        }
        closeView(view);
    }

    public void handleViewerDisconnect(Player viewer) {
        latestOpenRequests.remove(viewer.getUniqueId());
        cancelPendingReservations(viewer.getUniqueId());
        InvToolsView view = viewsByViewer.get(viewer.getUniqueId());
        if (view != null) {
            if (view.isolatesViewerCursor()) {
                viewer.setItemOnCursor(null);
            }
            closeView(view);
        }
    }

    public void handleTargetQuit(Player target) {
        Collection<InvToolsView> views = ListCopy.of(
                viewsByTarget.getOrDefault(target.getUniqueId(), Set.of())
        );
        for (InvToolsView view : views) {
            if (!view.onlineSession()) {
                continue;
            }
            view.freeze();
            detachVisible(view);
            Player viewer = Bukkit.getPlayer(view.viewerId());
            if (viewer != null && viewer.isOnline()) {
                closeIfViewing(viewer, view);
                send(viewer, "invtools.target_went_offline", "player", view.targetName());
            }
        }
    }

    /**
     * Runs on Paper's async pre-login thread, before the server loads the player's data.
     */
    public LoginBarrierResult prepareLogin(UUID playerId) {
        OfflineAccess access;
        InvToolsView view;
        InvToolsView.OfflineSavePlan plan;
        synchronized (transitionLock(playerId)) {
            access = offlineAccesses.get(playerId);
            if (access == null) {
                return LoginBarrierResult.ALLOW;
            }
            if (access instanceof OfflineReservation reservation) {
                reservation.cancel();
                offlineAccesses.remove(playerId, reservation);
                notifyReservationCancelledByLogin(reservation);
                return LoginBarrierResult.ALLOW;
            }

            view = ((ActiveOfflineView) access).view();
            try {
                plan = view.beginOfflineSave();
            } catch (RuntimeException exception) {
                view.closeWithoutSaving();
                detachVisible(view);
                offlineAccesses.remove(playerId, access);
                feature.getLogger().warning(
                        "Could not settle offline InvTools state before login for " + playerId
                                + ": " + exception.getMessage()
                );
                scheduleCloseAfterTransition(view, InvToolsView.SaveResult.FAILED);
                return LoginBarrierResult.RETRY;
            }
            if (plan == null) {
                offlineAccesses.remove(playerId, access);
                return LoginBarrierResult.ALLOW;
            }
            detachVisible(view);
        }

        InvToolsView.SaveResult result;
        if (plan.newlyStarted()) {
            startPersistence(plan);
        }
        result = await(plan.completion());

        if (result == null) {
            return LoginBarrierResult.RETRY;
        }
        view.finishSave(result);
        offlineAccesses.remove(playerId, access);
        scheduleCloseAfterTransition(view, result);
        return result == InvToolsView.SaveResult.SAVED
                || result == InvToolsView.SaveResult.UNCHANGED
                ? LoginBarrierResult.ALLOW
                : LoginBarrierResult.RETRY;
    }

    /**
     * Main-thread fallback for a login that began immediately before an offline reservation.
     * Player data is already loading at this point, so pending GUI edits are discarded.
     */
    public void handlePlayerDataLoad(UUID playerId) {
        if (playerId == null) {
            return;
        }
        InvToolsView view;
        synchronized (transitionLock(playerId)) {
            OfflineAccess access = offlineAccesses.remove(playerId);
            if (access instanceof OfflineReservation reservation) {
                reservation.cancel();
                notifyReservationCancelledByLogin(reservation);
                return;
            }
            if (!(access instanceof ActiveOfflineView activeView)) {
                return;
            }
            view = activeView.view();
            view.closeWithoutSaving();
            detachVisible(view);
        }
        scheduleMain(() -> {
            Player viewer = Bukkit.getPlayer(view.viewerId());
            if (viewer != null && viewer.isOnline()) {
                closeIfViewing(viewer, view);
                send(viewer, "invtools.join_conflict_discarded",
                        "player", view.targetName());
            }
        });
    }

    public void shutdown() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        offlineAccesses.values().stream()
                .filter(OfflineReservation.class::isInstance)
                .map(OfflineReservation.class::cast)
                .forEach(OfflineReservation::cancel);

        for (InvToolsView view : ListCopy.of(viewsByViewer.values())) {
            detachVisible(view);
            if (view.onlineSession()) {
                view.freeze();
            } else {
                InvToolsView.OfflineSavePlan plan = view.beginOfflineSave();
                if (plan != null) {
                    InvToolsView.SaveResult result = plan.newlyStarted()
                            ? persist(plan)
                            : await(plan.completion());
                    view.finishSave(result == null ? InvToolsView.SaveResult.FAILED : result);
                }
            }
            Player viewer = Bukkit.getPlayer(view.viewerId());
            if (viewer != null && viewer.isOnline()) {
                closeIfViewing(viewer, view);
            }
        }

        viewsByViewer.clear();
        viewsByTarget.clear();
        editorsByTarget.clear();
        offlineAccesses.clear();
        latestOpenRequests.clear();
    }

    private void openResolved(
            Player viewer,
            UUID targetId,
            String targetName,
            InventoryKind kind,
            UUID requestId
    ) {
        UUID viewerId = viewer.getUniqueId();
        if (!isCurrentRequest(viewerId, requestId)) {
            return;
        }
        if (viewerId.equals(targetId)) {
            latestOpenRequests.remove(viewerId, requestId);
            send(viewer, "invtools.self");
            return;
        }
        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            latestOpenRequests.remove(viewerId, requestId);
            openOnline(viewer, onlineTarget, kind);
            return;
        }
        boolean editable = hasEditPermission(viewer, kind);
        if (editable && !isEmpty(viewer.getItemOnCursor())) {
            latestOpenRequests.remove(viewerId, requestId);
            send(viewer, "invtools.cursor_not_empty");
            return;
        }

        OfflineReservation reservation = new OfflineReservation(
                viewerId,
                targetId,
                targetName,
                kind,
                editable,
                requestId
        );
        synchronized (transitionLock(targetId)) {
            if (!isCurrentRequest(viewerId, requestId)) {
                return;
            }
            if (offlineAccesses.putIfAbsent(targetId, reservation) != null) {
                latestOpenRequests.remove(viewerId, requestId);
                send(viewer, "invtools.already_open", "player", targetName);
                return;
            }
        }

        try {
            feature.getLifecycleManager().getTaskManager().supplyAsync(() ->
                    loadOffline(targetId)
            ).whenComplete((loaded, failure) -> scheduleMain(() ->
                    completeOfflineOpen(viewer, reservation, loaded, failure)
            ));
        } catch (RuntimeException exception) {
            offlineAccesses.remove(targetId, reservation);
            latestOpenRequests.remove(viewerId, requestId);
            feature.getLogger().warning(
                    "Could not schedule offline playerdata load for " + targetId + ": "
                            + exception.getMessage()
            );
            send(viewer, "invtools.load_failed", "player", targetName);
        }
    }

    private void completeOfflineOpen(
            Player viewer,
            OfflineReservation reservation,
            OfflinePlayerData loaded,
            Throwable failure
    ) {
        if (!active.get() || reservation.cancelled()
                || !isCurrentRequest(reservation.viewerId(), reservation.requestId())
                || offlineAccesses.get(reservation.targetId()) != reservation) {
            return;
        }
        if (!canStillOpen(viewer, reservation.kind())) {
            offlineAccesses.remove(reservation.targetId(), reservation);
            latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
            return;
        }
        if (failure == null && loaded == null) {
            offlineAccesses.remove(reservation.targetId(), reservation);
            latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
            send(viewer, "invtools.not_played_here", "player", reservation.targetName());
            return;
        }
        if (failure != null) {
            offlineAccesses.remove(reservation.targetId(), reservation);
            latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
            feature.getLogger().warning(
                    "Could not load offline playerdata for " + reservation.targetId() + ": "
                            + (failure == null ? "no data returned" : rootCause(failure).getMessage())
            );
            send(viewer, "invtools.load_failed", "player", reservation.targetName());
            return;
        }
        if (reservation.editable() && !isEmpty(viewer.getItemOnCursor())) {
            offlineAccesses.remove(reservation.targetId(), reservation);
            latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
            send(viewer, "invtools.cursor_not_empty");
            return;
        }

        closeExistingViewerSession(viewer);
        synchronized (transitionLock(reservation.targetId())) {
            if (!active.get() || reservation.cancelled()
                    || !isCurrentRequest(reservation.viewerId(), reservation.requestId())
                    || offlineAccesses.get(reservation.targetId()) != reservation) {
                return;
            }
            Player nowOnline = Bukkit.getPlayer(reservation.targetId());
            if (nowOnline != null && nowOnline.isOnline()) {
                offlineAccesses.remove(reservation.targetId(), reservation);
                latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
                openOnline(viewer, nowOnline, reservation.kind());
                return;
            }

            boolean safelyEditable = reservation.editable()
                    && hasEditPermission(viewer, reservation.kind())
                    && loaded.supportsSafeEditing();
            InvToolsView view;
            try {
                view = new InvToolsView(
                        viewer,
                        reservation.targetId(),
                        reservation.targetName(),
                        reservation.kind(),
                        false,
                        safelyEditable,
                        loaded.snapshot(),
                        loaded
                );
                ActiveOfflineView access = new ActiveOfflineView(view);
                if (!offlineAccesses.replace(reservation.targetId(), reservation, access)) {
                    return;
                }
                registerAndOpen(viewer, view);
                latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
                sendOpened(viewer, view);
                if (reservation.editable() && !loaded.supportsSafeEditing()) {
                    send(viewer, "invtools.outdated_data_read_only",
                            "player", reservation.targetName());
                }
            } catch (RuntimeException exception) {
                offlineAccesses.remove(reservation.targetId());
                latestOpenRequests.remove(reservation.viewerId(), reservation.requestId());
                feature.getLogger().warning(
                        "Could not open offline InvTools view for " + reservation.targetId()
                                + ": " + exception.getMessage()
                );
                send(viewer, "invtools.open_failed", "player", reservation.targetName());
            }
        }
    }

    private void openOnline(Player viewer, Player target, InventoryKind kind) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            send(viewer, "invtools.self");
            return;
        }
        closeExistingViewerSession(viewer);

        boolean requestedEdit = hasEditPermission(viewer, kind);
        boolean editable = requestedEdit;
        if (requestedEdit) {
            UUID existingEditor = editorsByTarget.putIfAbsent(
                    target.getUniqueId(),
                    viewer.getUniqueId()
            );
            if (existingEditor != null && !existingEditor.equals(viewer.getUniqueId())) {
                send(viewer, "invtools.already_editing", "player", target.getName());
                editable = false;
            }
        }

        InvToolsView view;
        try {
            view = new InvToolsView(
                    viewer,
                    target.getUniqueId(),
                    target.getName(),
                    kind,
                    true,
                    editable,
                    InventorySnapshot.capture(target),
                    null
            );
        } catch (RuntimeException exception) {
            if (editable) {
                editorsByTarget.remove(target.getUniqueId(), viewer.getUniqueId());
            }
            feature.getLogger().warning(
                    "Could not create online InvTools view for " + target.getUniqueId()
                            + ": " + exception.getMessage()
            );
            send(viewer, "invtools.open_failed", "player", target.getName());
            return;
        }
        try {
            registerAndOpen(viewer, view);
            sendOpened(viewer, view);
        } catch (RuntimeException exception) {
            if (editable) {
                editorsByTarget.remove(target.getUniqueId(), viewer.getUniqueId());
            }
            feature.getLogger().warning(
                    "Could not open online InvTools view for " + target.getUniqueId()
                            + ": " + exception.getMessage()
            );
            send(viewer, "invtools.open_failed", "player", target.getName());
        }
    }

    private void registerAndOpen(Player viewer, InvToolsView view) {
        viewsByViewer.put(view.viewerId(), view);
        viewsByTarget.computeIfAbsent(view.targetId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(view);
        try {
            viewer.openInventory(view.getInventory());
        } catch (RuntimeException exception) {
            detachVisible(view);
            throw exception;
        }
    }

    private void closeExistingViewerSession(Player viewer) {
        InvToolsView existing = viewsByViewer.get(viewer.getUniqueId());
        if (existing != null) {
            if (existing.isolatesViewerCursor()) {
                viewer.setItemOnCursor(null);
            }
            closeView(existing);
        }
    }

    private void closeView(InvToolsView view) {
        detachVisible(view);
        if (view.onlineSession()) {
            view.freeze();
            return;
        }

        InvToolsView.OfflineSavePlan plan;
        synchronized (transitionLock(view.targetId())) {
            try {
                plan = view.beginOfflineSave();
            } catch (RuntimeException exception) {
                view.closeWithoutSaving();
                offlineAccesses.remove(view.targetId(), new ActiveOfflineView(view));
                feature.getLogger().warning(
                        "Could not settle offline InvTools cursor for " + view.targetId()
                                + ": " + exception.getMessage()
                );
                notifySaveResult(view, InvToolsView.SaveResult.FAILED);
                return;
            }
        }
        if (plan == null || !plan.newlyStarted()) {
            return;
        }
        if (!plan.dirty()) {
            view.finishSave(InvToolsView.SaveResult.UNCHANGED);
            offlineAccesses.remove(view.targetId(), new ActiveOfflineView(view));
            return;
        }

        plan.completion().whenComplete((result, failure) -> {
            InvToolsView.SaveResult resolved = failure == null && result != null
                    ? result
                    : InvToolsView.SaveResult.FAILED;
            view.finishSave(resolved);
            offlineAccesses.remove(view.targetId(), new ActiveOfflineView(view));
            scheduleMain(() -> notifySaveResult(view, resolved));
        });
        startPersistence(plan);
    }

    private void startPersistence(InvToolsView.OfflineSavePlan plan) {
        try {
            feature.getLifecycleManager().getTaskManager().runAsync(() -> {
                InvToolsView.SaveResult result = persist(plan);
                plan.completion().complete(result);
            }).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    feature.getLogger().warning(
                            "InvTools offline save task failed: "
                                    + rootCause(failure).getMessage()
                    );
                    plan.completion().complete(InvToolsView.SaveResult.FAILED);
                }
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not schedule InvTools offline save: " + exception.getMessage()
            );
            plan.completion().complete(InvToolsView.SaveResult.FAILED);
        }
    }

    private InvToolsView.SaveResult persist(InvToolsView.OfflineSavePlan plan) {
        if (!plan.dirty()) {
            return InvToolsView.SaveResult.UNCHANGED;
        }
        try {
            offlineStore.save(plan.original(), plan.kind(), plan.changedSnapshot());
            return InvToolsView.SaveResult.SAVED;
        } catch (PlayerDataConflictException exception) {
            feature.getLogger().warning(
                    "Refused conflicting offline playerdata save for "
                            + plan.original().playerId() + ": " + exception.getMessage()
            );
            return InvToolsView.SaveResult.CONFLICT;
        } catch (IOException | RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not save offline playerdata for " + plan.original().playerId() + ": "
                            + exception.getMessage()
            );
            return InvToolsView.SaveResult.FAILED;
        }
    }

    private OfflinePlayerData loadOffline(UUID playerId) {
        try {
            if (!offlineStore.hasPlayerData(playerId)) {
                return null;
            }
            return offlineStore.load(playerId);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private InvToolsView.SaveResult await(CompletableFuture<InvToolsView.SaveResult> future) {
        try {
            return future.get(loginBarrierTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            feature.getLogger().warning("Timed out waiting for an InvTools playerdata save.");
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            feature.getLogger().warning(
                    "InvTools playerdata save failed: " + rootCause(exception).getMessage()
            );
            return InvToolsView.SaveResult.FAILED;
        }
    }

    private void applyMutation(InvToolsView view, int backingSlot, ItemStack item) {
        if (!view.onlineSession()) {
            return;
        }
        Player target = Bukkit.getPlayer(view.targetId());
        if (target == null || !target.isOnline()) {
            closeOnlineTargetViews(view.targetId());
            return;
        }

        ItemStack changed = cloneOrNull(item);
        if (view.kind() == InventoryKind.ENDER_CHEST) {
            target.getEnderChest().setItem(backingSlot, changed);
        } else if (backingSlot >= 0 && backingSlot < InventorySnapshot.STORAGE_SIZE) {
            target.getInventory().setItem(backingSlot, changed);
        } else {
            switch (backingSlot) {
                case InventorySnapshot.BOOTS_SLOT -> target.getInventory().setBoots(changed);
                case InventorySnapshot.LEGGINGS_SLOT -> target.getInventory().setLeggings(changed);
                case InventorySnapshot.CHESTPLATE_SLOT -> target.getInventory().setChestplate(changed);
                case InventorySnapshot.HELMET_SLOT -> target.getInventory().setHelmet(changed);
                case InventorySnapshot.OFF_HAND_SLOT -> target.getInventory().setItemInOffHand(changed);
                default -> throw new IllegalArgumentException(
                        "Unsupported online inventory slot: " + backingSlot
                );
            }
        }
        target.updateInventory();

        InventorySnapshot latest = InventorySnapshot.capture(target);
        for (InvToolsView targetView : ListCopy.of(
                viewsByTarget.getOrDefault(view.targetId(), Set.of())
        )) {
            if (targetView.onlineSession()) {
                targetView.refresh(latest);
            }
        }
    }

    private void closeOnlineTargetViews(UUID targetId) {
        for (InvToolsView view : ListCopy.of(viewsByTarget.getOrDefault(targetId, Set.of()))) {
            if (view.isolatesViewerCursor()) {
                continue;
            }
            view.freeze();
            detachVisible(view);
            Player viewer = Bukkit.getPlayer(view.viewerId());
            if (viewer != null && viewer.isOnline()) {
                closeIfViewing(viewer, view);
                send(viewer, "invtools.target_went_offline", "player", view.targetName());
            }
        }
    }

    private void detachVisible(InvToolsView view) {
        viewsByViewer.remove(view.viewerId(), view);
        Set<InvToolsView> targetViews = viewsByTarget.get(view.targetId());
        if (targetViews != null) {
            targetViews.remove(view);
            if (targetViews.isEmpty()) {
                viewsByTarget.remove(view.targetId(), targetViews);
            }
        }
        if (view.editable() && view.onlineSession()) {
            editorsByTarget.remove(view.targetId(), view.viewerId());
        }
    }

    private void scheduleCloseAfterTransition(
            InvToolsView view,
            InvToolsView.SaveResult result
    ) {
        scheduleMain(() -> {
            Player viewer = Bukkit.getPlayer(view.viewerId());
            if (viewer != null && viewer.isOnline()) {
                closeIfViewing(viewer, view);
                send(viewer, "invtools.target_logging_in", "player", view.targetName());
                notifySaveResult(view, result);
            }
        });
    }

    private void notifySaveResult(InvToolsView view, InvToolsView.SaveResult result) {
        Player viewer = Bukkit.getPlayer(view.viewerId());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        if (result == InvToolsView.SaveResult.SAVED) {
            send(viewer, "invtools.offline_saved", "player", view.targetName());
        } else if (result == InvToolsView.SaveResult.CONFLICT) {
            send(viewer, "invtools.save_conflict", "player", view.targetName());
        } else if (result == InvToolsView.SaveResult.FAILED) {
            send(viewer, "invtools.save_failed", "player", view.targetName());
        }
    }

    private void sendOpened(Player viewer, InvToolsView view) {
        send(
                viewer,
                view.editable() ? "invtools.opened_edit" : "invtools.opened_inspect",
                "player",
                view.targetName()
        );
    }

    private void cancelPendingReservations(UUID viewerId) {
        for (Map.Entry<UUID, OfflineAccess> entry : offlineAccesses.entrySet()) {
            if (!(entry.getValue() instanceof OfflineReservation reservation)
                    || !reservation.viewerId().equals(viewerId)) {
                continue;
            }
            synchronized (transitionLock(entry.getKey())) {
                if (offlineAccesses.remove(entry.getKey(), reservation)) {
                    reservation.cancel();
                }
            }
        }
    }

    private boolean isCurrentRequest(UUID viewerId, UUID requestId) {
        return requestId.equals(latestOpenRequests.get(viewerId));
    }

    private void notifyReservationCancelledByLogin(OfflineReservation reservation) {
        scheduleMain(() -> {
            Player viewer = Bukkit.getPlayer(reservation.viewerId());
            if (viewer != null && viewer.isOnline()) {
                send(viewer, "invtools.target_logging_in", "player", reservation.targetName());
            }
        });
    }

    private void auditMutation(
            Player actor,
            InvToolsView view,
            int backingSlot,
            ItemStack before,
            ItemStack after
    ) {
        if (!auditEdits || sameItem(before, after)) {
            return;
        }
        int guiSlot = InventorySlotLayout.guiSlot(view.kind(), backingSlot).orElse(-1);
        InventorySlotLayout.SlotSection section =
                InventorySlotLayout.section(view.kind(), guiSlot);
        feature.getLogger().info(
                "InvTools edit: actor=" + actor.getName() + "/" + actor.getUniqueId()
                        + ", target=" + view.targetName() + "/" + view.targetId()
                        + ", source=" + (view.onlineSession() ? "online" : "offline")
                        + ", inventory=" + view.kind()
                        + ", section=" + section
                        + ", slot=" + backingSlot
                        + ", before=" + describeItem(before)
                        + ", after=" + describeItem(after)
        );
    }

    public static String inspectPermission(InventoryKind kind) {
        return PERMISSION_PREFIX + kind.commandName() + ".inspect";
    }

    public static String editPermission(InventoryKind kind) {
        return PERMISSION_PREFIX + kind.commandName() + ".edit";
    }

    private static boolean hasInspectPermission(Player viewer, InventoryKind kind) {
        return viewer.hasPermission(inspectPermission(kind));
    }

    private static boolean hasEditPermission(Player viewer, InventoryKind kind) {
        return viewer.hasPermission(editPermission(kind));
    }

    private boolean canStillOpen(Player viewer, InventoryKind kind) {
        return active.get()
                && viewer.isOnline()
                && hasInspectPermission(viewer, kind);
    }

    private void scheduleMain(Runnable task) {
        if (!active.get()) {
            return;
        }
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(task);
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not schedule InvTools main-thread continuation: " + exception.getMessage()
            );
        }
    }

    private void send(Player player, String key) {
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .forAudience(player)
                .build());
    }

    private void send(Player player, String key, String placeholder, String value) {
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .forAudience(player)
                .with(placeholder, value)
                .build());
    }

    private static boolean canRouteThroughTop(InventoryAction action) {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_SWAP;
    }

    private static boolean allowsItemInSlot(InventoryKind kind, int backingSlot, ItemStack item) {
        if (kind != InventoryKind.PLAYER || isEmpty(item)) {
            return true;
        }
        EquipmentSlot expected = switch (backingSlot) {
            case InventorySnapshot.BOOTS_SLOT -> EquipmentSlot.FEET;
            case InventorySnapshot.LEGGINGS_SLOT -> EquipmentSlot.LEGS;
            case InventorySnapshot.CHESTPLATE_SLOT -> EquipmentSlot.CHEST;
            case InventorySnapshot.HELMET_SLOT -> EquipmentSlot.HEAD;
            default -> null;
        };
        return expected == null || item.getType().getEquipmentSlot() == expected;
    }

    private static InvToolsView holder(org.bukkit.inventory.Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof InvToolsView view
                ? view
                : null;
    }

    private static void closeIfViewing(Player viewer, InvToolsView view) {
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == view) {
            if (!view.onlineSession()) {
                viewer.setItemOnCursor(null);
            }
            viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    private Object transitionLock(UUID playerId) {
        return transitionLocks[Math.floorMod(playerId.hashCode(), transitionLocks.length)];
    }

    private static Object[] createTransitionLocks() {
        Object[] locks = new Object[TRANSITION_LOCK_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        ItemStack normalizedFirst = cloneOrNull(first);
        ItemStack normalizedSecond = cloneOrNull(second);
        if (normalizedFirst == null || normalizedSecond == null) {
            return normalizedFirst == null && normalizedSecond == null;
        }
        return normalizedFirst.getAmount() == normalizedSecond.getAmount()
                && normalizedFirst.isSimilar(normalizedSecond);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static String describeItem(ItemStack item) {
        if (isEmpty(item)) {
            return "empty";
        }
        return item.getAmount() + "x" + item.getType().name();
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0
                ? null
                : item.clone();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current.getCause() != null)
                && current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public enum LoginBarrierResult {
        ALLOW,
        RETRY
    }

    private sealed interface OfflineAccess permits OfflineReservation, ActiveOfflineView {
    }

    private record ActiveOfflineView(InvToolsView view) implements OfflineAccess {
        private ActiveOfflineView {
            Objects.requireNonNull(view, "view");
        }
    }

    private static final class OfflineReservation implements OfflineAccess {
        private final UUID viewerId;
        private final UUID targetId;
        private final String targetName;
        private final InventoryKind kind;
        private final boolean editable;
        private final UUID requestId;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private OfflineReservation(
                UUID viewerId,
                UUID targetId,
                String targetName,
                InventoryKind kind,
                boolean editable,
                UUID requestId
        ) {
            this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
            this.targetId = Objects.requireNonNull(targetId, "targetId");
            this.targetName = Objects.requireNonNull(targetName, "targetName");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.editable = editable;
            this.requestId = Objects.requireNonNull(requestId, "requestId");
        }

        private UUID viewerId() {
            return viewerId;
        }

        private UUID targetId() {
            return targetId;
        }

        private String targetName() {
            return targetName;
        }

        private InventoryKind kind() {
            return kind;
        }

        private boolean editable() {
            return editable;
        }

        private UUID requestId() {
            return requestId;
        }

        private void cancel() {
            cancelled.set(true);
        }

        private boolean cancelled() {
            return cancelled.get();
        }
    }

    private static final class ListCopy {
        private ListCopy() {
        }

        private static <T> java.util.List<T> of(Collection<T> values) {
            return values == null || values.isEmpty()
                    ? java.util.List.of()
                    : new ArrayList<>(values);
        }
    }
}
