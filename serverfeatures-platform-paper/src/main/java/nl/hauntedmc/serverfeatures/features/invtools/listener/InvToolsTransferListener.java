package nl.hauntedmc.serverfeatures.features.invtools.listener;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InventorySlotLayout;
import nl.hauntedmc.serverfeatures.features.invtools.gui.PlayerStorageTransfer;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles deterministic shift transfers before the generic InvTools click listener rejects Bukkit's
 * unsafe bulk-routing behavior.
 */
public final class InvToolsTransferListener implements Listener {

    private final InvTools feature;
    private final Map<UUID, InvToolsView> settlingViews = new ConcurrentHashMap<>();
    private final Set<UUID> scheduledSettlementPolls = ConcurrentHashMap.newKeySet();

    public InvToolsTransferListener(InvTools feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (settlingViews.containsKey(viewer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }

        InvToolsView view = holder(event.getView().getTopInventory());
        if (view == null || !view.viewerId().equals(viewer.getUniqueId())) {
            return;
        }
        if (!view.editable()
                || !viewer.hasPermission(InvToolsService.openEditPermission(view.kind()))) {
            // The normal InvTools listener provides the existing read-only/revocation feedback.
            return;
        }

        event.setCancelled(true);
        if (!view.isInteractive() || event.getClickedInventory() == null) {
            return;
        }

        try {
            if (view.owns(event.getClickedInventory())) {
                transferFromTarget(viewer, view, event.getSlot());
            } else if (event.getClickedInventory() == viewer.getInventory()) {
                transferIntoTarget(viewer, view, event.getSlot());
            }
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "InvTools shift transfer failed for viewer " + viewer.getUniqueId()
                            + " and target " + view.targetId() + ": " + exception.getMessage()
            );
            viewer.updateInventory();
            view.refresh(view.snapshot());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        InvToolsView view = holder(event.getView().getTopInventory());
        if (view == null || view.onlineSession() || !view.hasViewerTransfers()) {
            return;
        }
        settlingViews.put(viewer.getUniqueId(), view);
        scheduleSettlementPoll(viewer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player viewer = event.getPlayer();
        InvToolsView view = settlingViews.get(viewer.getUniqueId());
        if (view == null) {
            view = holder(viewer.getOpenInventory().getTopInventory());
        }
        if (view != null && !view.onlineSession()) {
            view.abortOfflineTransfersForDisconnect();
        }
        settlingViews.remove(viewer.getUniqueId());
        scheduledSettlementPolls.remove(viewer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (settlingViews.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (settlingViews.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (settlingViews.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (settlingViews.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (settlingViews.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && settlingViews.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void transferFromTarget(Player viewer, InvToolsView view, int guiSlot) {
        var backingSlot = InventorySlotLayout.backingSlot(view.kind(), guiSlot);
        if (backingSlot.isEmpty()) {
            return;
        }

        if (view.onlineSession()) {
            transferOnlineFromTarget(viewer, view, backingSlot.getAsInt());
        } else {
            transferOfflineFromTarget(viewer, view, backingSlot.getAsInt());
        }
    }

    private void transferIntoTarget(Player viewer, InvToolsView view, int viewerSlot) {
        if (viewerSlot < 0 || viewerSlot >= InventorySnapshot.STORAGE_SIZE) {
            return;
        }
        if (view.onlineSession()) {
            transferOnlineIntoTarget(viewer, view, viewerSlot);
        } else {
            transferOfflineIntoTarget(viewer, view, viewerSlot);
        }
    }

    private void transferOnlineFromTarget(Player viewer, InvToolsView view, int backingSlot) {
        Player target = Bukkit.getPlayer(view.targetId());
        if (target == null || !target.isOnline()) {
            return;
        }

        InventorySnapshot displayed = view.snapshot();
        InventorySnapshot live = InventorySnapshot.capture(target);
        ItemStack current = live.itemAt(view.kind(), backingSlot);
        if (!sameItem(displayed.itemAt(view.kind(), backingSlot), current)) {
            view.refresh(live);
            return;
        }
        if (isEmpty(current)) {
            return;
        }

        ItemStack[] viewerStorage = viewer.getInventory().getStorageContents();
        PlayerStorageTransfer.InsertionResult insertion =
                PlayerStorageTransfer.insert(viewerStorage, current);
        int transferred = PlayerStorageTransfer.transferredAmount(current, insertion.remainder());
        if (transferred <= 0) {
            return;
        }

        InventorySnapshot changedTarget = live.withBackingSlot(
                view.kind(),
                backingSlot,
                insertion.remainder()
        );
        applyOnlineTransfer(viewer, target, view, live, changedTarget, insertion.storage());
        auditChanges(viewer, view, live, changedTarget);
    }

    private void transferOnlineIntoTarget(Player viewer, InvToolsView view, int viewerSlot) {
        Player target = Bukkit.getPlayer(view.targetId());
        if (target == null || !target.isOnline()) {
            return;
        }

        ItemStack[] viewerStorage = viewer.getInventory().getStorageContents();
        ItemStack offered = cloneOrNull(viewerStorage[viewerSlot]);
        if (offered == null) {
            return;
        }

        InventorySnapshot live = InventorySnapshot.capture(target);
        InventorySnapshot.InsertionResult insertion = live.shiftInsert(view.kind(), offered);
        int transferred = PlayerStorageTransfer.transferredAmount(offered, insertion.remainder());
        if (transferred <= 0) {
            view.refresh(live);
            return;
        }

        ItemStack[] changedViewerStorage = PlayerStorageTransfer.decrementSlot(
                viewerStorage,
                viewerSlot,
                transferred
        );
        applyOnlineTransfer(
                viewer,
                target,
                view,
                live,
                insertion.snapshot(),
                changedViewerStorage
        );
        auditChanges(viewer, view, live, insertion.snapshot());
    }

    private void transferOfflineFromTarget(Player viewer, InvToolsView view, int backingSlot) {
        InventorySnapshot currentSnapshot = view.snapshot();
        ItemStack current = currentSnapshot.itemAt(view.kind(), backingSlot);
        if (isEmpty(current)) {
            return;
        }

        ItemStack[] viewerStorage = viewer.getInventory().getStorageContents();
        PlayerStorageTransfer.InsertionResult insertion =
                PlayerStorageTransfer.insert(viewerStorage, current);
        int transferred = PlayerStorageTransfer.transferredAmount(current, insertion.remainder());
        if (transferred <= 0) {
            return;
        }

        ItemStack transferredItem = withAmount(current, transferred);
        InventorySnapshot changedTarget = currentSnapshot.withBackingSlot(
                view.kind(),
                backingSlot,
                insertion.remainder()
        );
        if (view.applyOfflineShiftTransfer(
                changedTarget,
                viewerStorage,
                insertion.storage(),
                transferredItem,
                true
        )) {
            auditChanges(viewer, view, currentSnapshot, changedTarget);
        }
    }

    private void transferOfflineIntoTarget(Player viewer, InvToolsView view, int viewerSlot) {
        // Keep the isolated target cursor settleable: do not fill target capacity behind it.
        if (!isEmpty(view.cursor())) {
            return;
        }

        ItemStack[] viewerStorage = viewer.getInventory().getStorageContents();
        ItemStack offered = cloneOrNull(viewerStorage[viewerSlot]);
        if (offered == null) {
            return;
        }

        InventorySnapshot currentSnapshot = view.snapshot();
        InventorySnapshot.InsertionResult insertion =
                currentSnapshot.shiftInsert(view.kind(), offered);
        int transferred = PlayerStorageTransfer.transferredAmount(offered, insertion.remainder());
        if (transferred <= 0) {
            return;
        }

        ItemStack transferredItem = withAmount(offered, transferred);
        ItemStack[] changedViewerStorage = PlayerStorageTransfer.decrementSlot(
                viewerStorage,
                viewerSlot,
                transferred
        );
        if (view.applyOfflineShiftTransfer(
                insertion.snapshot(),
                viewerStorage,
                changedViewerStorage,
                transferredItem,
                false
        )) {
            auditChanges(viewer, view, currentSnapshot, insertion.snapshot());
        }
    }

    private void applyOnlineTransfer(
            Player viewer,
            Player target,
            InvToolsView view,
            InventorySnapshot before,
            InventorySnapshot after,
            ItemStack[] changedViewerStorage
    ) {
        ItemStack[] originalViewerStorage = viewer.getInventory().getStorageContents();
        try {
            viewer.getInventory().setStorageContents(changedViewerStorage);
            applyTargetSnapshot(target, view.kind(), before, after);
            view.refresh(after);
            viewer.updateInventory();
        } catch (RuntimeException exception) {
            viewer.getInventory().setStorageContents(originalViewerStorage);
            applyTargetSnapshot(target, view.kind(), after, before);
            view.refresh(before);
            viewer.updateInventory();
            throw exception;
        }
    }

    private static void applyTargetSnapshot(
            Player target,
            InventoryKind kind,
            InventorySnapshot before,
            InventorySnapshot after
    ) {
        for (int backingSlot : before.changedBackingSlots(kind, after)) {
            ItemStack item = after.itemAt(kind, backingSlot);
            if (kind == InventoryKind.ENDER_CHEST) {
                target.getEnderChest().setItem(backingSlot, item);
            } else if (backingSlot >= 0 && backingSlot < InventorySnapshot.STORAGE_SIZE) {
                target.getInventory().setItem(backingSlot, item);
            } else {
                setEquipment(target, backingSlot, item);
            }
        }
        target.updateInventory();
    }

    private static void setEquipment(Player target, int backingSlot, ItemStack item) {
        switch (backingSlot) {
            case InventorySnapshot.BOOTS_SLOT -> target.getInventory().setBoots(item);
            case InventorySnapshot.LEGGINGS_SLOT -> target.getInventory().setLeggings(item);
            case InventorySnapshot.CHESTPLATE_SLOT -> target.getInventory().setChestplate(item);
            case InventorySnapshot.HELMET_SLOT -> target.getInventory().setHelmet(item);
            case InventorySnapshot.OFF_HAND_SLOT -> target.getInventory().setItemInOffHand(item);
            default -> throw new IllegalArgumentException(
                    "Unsupported online inventory slot: " + backingSlot
            );
        }
    }

    private void auditChanges(
            Player actor,
            InvToolsView view,
            InventorySnapshot before,
            InventorySnapshot after
    ) {
        if (!feature.getConfigHandler().get("audit_edits", Boolean.class, true)) {
            return;
        }
        for (int backingSlot : before.changedBackingSlots(view.kind(), after)) {
            ItemStack oldItem = before.itemAt(view.kind(), backingSlot);
            ItemStack newItem = after.itemAt(view.kind(), backingSlot);
            int guiSlot = InventorySlotLayout.guiSlot(view.kind(), backingSlot).orElse(-1);
            EquipmentSlot ignored = null;
            feature.getLogger().info(
                    "InvTools edit: actor=" + actor.getName() + "/" + actor.getUniqueId()
                            + ", target=" + view.targetName() + "/" + view.targetId()
                            + ", session=" + view.sessionId()
                            + ", source=" + (view.onlineSession() ? "online" : "offline")
                            + ", outcome=" + (view.onlineSession() ? "applied" : "pending")
                            + ", inventory=" + view.kind()
                            + ", section=" + InventorySlotLayout.section(view.kind(), guiSlot)
                            + ", slot=" + backingSlot
                            + ", before=" + describeItem(oldItem)
                            + ", after=" + describeItem(newItem)
            );
        }
    }

    private void scheduleSettlementPoll(UUID viewerId) {
        if (!scheduledSettlementPolls.add(viewerId)) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLater(
                    feature.getPlugin(),
                    () -> pollSettlement(viewerId),
                    1L
            );
        } catch (RuntimeException exception) {
            scheduledSettlementPolls.remove(viewerId);
            feature.getLogger().warning(
                    "Could not schedule InvTools transfer settlement: " + exception.getMessage()
            );
        }
    }

    private void pollSettlement(UUID viewerId) {
        InvToolsView view = settlingViews.get(viewerId);
        Player viewer = Bukkit.getPlayer(viewerId);
        if (view == null || viewer == null || !viewer.isOnline()) {
            settlingViews.remove(viewerId);
            scheduledSettlementPolls.remove(viewerId);
            return;
        }
        if (view.isClosed()) {
            settlingViews.remove(viewerId, view);
            scheduledSettlementPolls.remove(viewerId);
            if (holder(viewer.getOpenInventory().getTopInventory()) == view) {
                viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
            return;
        }

        if (holder(viewer.getOpenInventory().getTopInventory()) != view) {
            viewer.openInventory(view.getInventory());
        }
        try {
            Bukkit.getScheduler().runTaskLater(
                    feature.getPlugin(),
                    () -> pollSettlement(viewerId),
                    1L
            );
        } catch (RuntimeException exception) {
            settlingViews.remove(viewerId, view);
            scheduledSettlementPolls.remove(viewerId);
            feature.getLogger().warning(
                    "Could not continue InvTools transfer settlement: " + exception.getMessage()
            );
        }
    }

    private static InvToolsView holder(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof InvToolsView view
                ? view
                : null;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        ItemStack left = cloneOrNull(first);
        ItemStack right = cloneOrNull(second);
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }
        ItemStack changed = item.clone();
        changed.setAmount(amount);
        return changed;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static String describeItem(ItemStack item) {
        return isEmpty(item) ? "empty" : item.getAmount() + "x" + item.getType().name();
    }
}
