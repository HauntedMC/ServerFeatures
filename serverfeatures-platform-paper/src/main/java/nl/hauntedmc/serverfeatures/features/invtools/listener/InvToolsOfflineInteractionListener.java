package nl.hauntedmc.serverfeatures.features.invtools.listener;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InventorySlotLayout;
import nl.hauntedmc.serverfeatures.features.invtools.gui.OfflineCursorTransaction;
import nl.hauntedmc.serverfeatures.features.invtools.gui.PlayerStorageTransfer;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Provides normal cursor interaction for editable offline InvTools views while keeping every
 * cross-inventory movement inside the existing save/rollback journal.
 */
public final class InvToolsOfflineInteractionListener implements Listener {

    private final InvTools feature;
    private final boolean auditEdits;

    public InvToolsOfflineInteractionListener(InvTools feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.auditEdits = feature.getConfigHandler().get(
                "audit_edits",
                Boolean.class,
                true
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        InvToolsView view = holder(event.getView().getTopInventory());
        if (!editableOfflineView(view, viewer)) {
            return;
        }

        if (!sameItem(view.cursor(), event.getCursor())) {
            event.setCancelled(true);
            synchronizeCursor(viewer, view);
            feature.getLogger().warning(
                    "Corrected an unexpected cursor change in offline InvTools view for "
                            + viewer.getUniqueId()
            );
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!isEmpty(view.cursor())) {
                event.setCancelled(true);
                send(viewer, "invtools.cursor_finish_first");
            }
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory() == viewer.getInventory()
                && isHotbarSwap(event.getAction())) {
            handleViewerHotbarSwap(event, viewer, view);
            return;
        }

        SlotTarget target = slotTarget(event, viewer, view);
        if (target == null) {
            return;
        }
        event.setCancelled(true);
        if (!view.isInteractive()) {
            return;
        }

        InventorySnapshot beforeTarget = view.snapshot();
        ItemStack[] beforeViewer = viewer.getInventory().getStorageContents();
        ItemStack current = target.side() == OfflineCursorTransaction.Side.TARGET
                ? beforeTarget.itemAt(view.kind(), target.slot())
                : cloneOrNull(beforeViewer[target.slot()]);
        var planned = view.planOfflineCursor(target.side(), event.getAction(), current);
        if (planned.isEmpty()) {
            if (!isEmpty(view.cursor())
                    && view.cursorOwner() != target.side()
                    && isPickup(event.getAction())) {
                send(viewer, "invtools.cursor_cross_stack");
            }
            return;
        }

        OfflineCursorTransaction.Plan plan = planned.get();
        if (target.side() == OfflineCursorTransaction.Side.TARGET
                && !allowsItemInSlot(view.kind(), target.slot(), plan.result().slotItem())) {
            return;
        }

        InventorySnapshot changedTarget = target.side() == OfflineCursorTransaction.Side.TARGET
                ? beforeTarget.withBackingSlot(
                        view.kind(),
                        target.slot(),
                        plan.result().slotItem()
                )
                : beforeTarget;
        ItemStack[] changedViewer = PlayerStorageTransfer.copyStorage(beforeViewer);
        if (target.side() == OfflineCursorTransaction.Side.VIEWER) {
            changedViewer[target.slot()] = cloneOrNull(plan.result().slotItem());
        }

        try {
            if (!view.applyOfflineCursorMutation(
                    plan,
                    changedTarget,
                    beforeViewer,
                    changedViewer
            )) {
                synchronizeCursor(viewer, view);
                return;
            }
            synchronizeCursor(viewer, view);
            auditChanges(viewer, view, beforeTarget, changedTarget);
        } catch (RuntimeException exception) {
            feature.getLogger().log(
                    Level.WARNING,
                    "Offline InvTools cursor interaction failed for viewer "
                            + viewer.getUniqueId() + " and target " + view.targetId(),
                    exception
            );
            synchronizeCursor(viewer, view);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        InvToolsView view = holder(event.getView().getTopInventory());
        if (!editableOfflineView(view, viewer)) {
            return;
        }

        event.setCancelled(true);
        if (isEmpty(view.cursor()) || !sameItem(view.cursor(), event.getOldCursor())) {
            synchronizeCursor(viewer, view);
            return;
        }

        DragTarget dragTarget = dragTarget(event, view);
        if (dragTarget == null || view.cursorOwner() != dragTarget.side()) {
            send(viewer, "invtools.drag_one_inventory");
            synchronizeCursor(viewer, view);
            return;
        }

        InventorySnapshot beforeTarget = view.snapshot();
        InventorySnapshot changedTarget = beforeTarget;
        ItemStack[] beforeViewer = viewer.getInventory().getStorageContents();
        ItemStack[] changedViewer = PlayerStorageTransfer.copyStorage(beforeViewer);
        if (dragTarget.side() == OfflineCursorTransaction.Side.VIEWER) {
            for (Map.Entry<Integer, ItemStack> entry : dragTarget.items().entrySet()) {
                changedViewer[entry.getKey()] = cloneOrNull(entry.getValue());
            }
        } else {
            for (Map.Entry<Integer, ItemStack> entry : dragTarget.items().entrySet()) {
                if (!allowsItemInSlot(view.kind(), entry.getKey(), entry.getValue())) {
                    synchronizeCursor(viewer, view);
                    return;
                }
                changedTarget = changedTarget.withBackingSlot(
                        view.kind(),
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        try {
            if (!view.applyOfflineSameSideDrag(
                    changedTarget,
                    beforeViewer,
                    changedViewer,
                    event.getCursor(),
                    dragTarget.side()
            )) {
                synchronizeCursor(viewer, view);
                return;
            }
            synchronizeCursor(viewer, view);
            auditChanges(viewer, view, beforeTarget, changedTarget);
        } catch (RuntimeException exception) {
            feature.getLogger().log(
                    Level.WARNING,
                    "Offline InvTools drag failed for viewer " + viewer.getUniqueId()
                            + " and target " + view.targetId(),
                    exception
            );
            synchronizeCursor(viewer, view);
        }
    }

    private void handleViewerHotbarSwap(
            InventoryClickEvent event,
            Player viewer,
            InvToolsView view
    ) {
        event.setCancelled(true);
        if (!isEmpty(view.cursor())) {
            send(viewer, "invtools.cursor_finish_first");
            return;
        }
        int clicked = event.getSlot();
        int hotbar = event.getHotbarButton();
        if (clicked < 0 || clicked >= InventorySnapshot.STORAGE_SIZE
                || hotbar < 0 || hotbar >= 9 || clicked == hotbar) {
            return;
        }
        ItemStack[] storage = viewer.getInventory().getStorageContents();
        ItemStack temporary = storage[clicked];
        storage[clicked] = storage[hotbar];
        storage[hotbar] = temporary;
        viewer.getInventory().setStorageContents(storage);
        viewer.updateInventory();
    }

    private SlotTarget slotTarget(
            InventoryClickEvent event,
            Player viewer,
            InvToolsView view
    ) {
        if (view.owns(event.getClickedInventory())) {
            var backingSlot = InventorySlotLayout.backingSlot(view.kind(), event.getSlot());
            return backingSlot.isEmpty()
                    ? null
                    : new SlotTarget(
                            OfflineCursorTransaction.Side.TARGET,
                            backingSlot.getAsInt()
                    );
        }
        if (event.getClickedInventory() == viewer.getInventory()
                && event.getSlot() >= 0
                && event.getSlot() < InventorySnapshot.STORAGE_SIZE) {
            return new SlotTarget(
                    OfflineCursorTransaction.Side.VIEWER,
                    event.getSlot()
            );
        }
        return null;
    }

    private DragTarget dragTarget(InventoryDragEvent event, InvToolsView view) {
        OfflineCursorTransaction.Side side = null;
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        int topSize = event.getView().getTopInventory().getSize();
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            OfflineCursorTransaction.Side currentSide;
            int slot;
            if (rawSlot < topSize) {
                var backingSlot = InventorySlotLayout.backingSlot(view.kind(), rawSlot);
                if (backingSlot.isEmpty()) {
                    return null;
                }
                currentSide = OfflineCursorTransaction.Side.TARGET;
                slot = backingSlot.getAsInt();
            } else {
                currentSide = OfflineCursorTransaction.Side.VIEWER;
                slot = event.getView().convertSlot(rawSlot);
                if (slot < 0 || slot >= InventorySnapshot.STORAGE_SIZE) {
                    return null;
                }
            }
            if (side != null && side != currentSide) {
                return null;
            }
            side = currentSide;
            items.put(slot, cloneOrNull(entry.getValue()));
        }
        return side == null ? null : new DragTarget(side, Map.copyOf(items));
    }

    private boolean editableOfflineView(InvToolsView view, Player viewer) {
        return view != null
                && !view.onlineSession()
                && view.editable()
                && view.viewerId().equals(viewer.getUniqueId())
                && viewer.hasPermission(InvToolsService.openEditPermission(view.kind()));
    }

    private void synchronizeCursor(Player viewer, InvToolsView view) {
        viewer.setItemOnCursor(view.cursor());
        viewer.updateInventory();
    }

    private void auditChanges(
            Player actor,
            InvToolsView view,
            InventorySnapshot before,
            InventorySnapshot after
    ) {
        if (!auditEdits) {
            return;
        }
        for (int backingSlot : before.changedBackingSlots(view.kind(), after)) {
            ItemStack previous = before.itemAt(view.kind(), backingSlot);
            ItemStack changed = after.itemAt(view.kind(), backingSlot);
            if (sameItem(previous, changed)) {
                continue;
            }
            int guiSlot = InventorySlotLayout.guiSlot(view.kind(), backingSlot).orElse(-1);
            feature.getLogger().info(
                    "InvTools edit: actor=" + actor.getName() + "/" + actor.getUniqueId()
                            + ", target=" + view.targetName() + "/" + view.targetId()
                            + ", session=" + view.sessionId()
                            + ", source=offline"
                            + ", outcome=pending"
                            + ", inventory=" + view.kind()
                            + ", section=" + InventorySlotLayout.section(view.kind(), guiSlot)
                            + ", slot=" + backingSlot
                            + ", before=" + describeItem(previous)
                            + ", after=" + describeItem(changed)
            );
        }
    }

    private void send(Player viewer, String key) {
        viewer.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .forAudience(viewer)
                .build());
    }

    private static boolean isHotbarSwap(InventoryAction action) {
        return action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    private static boolean isPickup(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME;
    }

    private static boolean allowsItemInSlot(
            InventoryKind kind,
            int backingSlot,
            ItemStack item
    ) {
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

    private static boolean sameItem(ItemStack first, ItemStack second) {
        ItemStack left = cloneOrNull(first);
        ItemStack right = cloneOrNull(second);
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private static String describeItem(ItemStack item) {
        return isEmpty(item) ? "empty" : item.getAmount() + "x" + item.getType().name();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private record SlotTarget(OfflineCursorTransaction.Side side, int slot) {
        private SlotTarget {
            Objects.requireNonNull(side, "side");
        }
    }

    private record DragTarget(
            OfflineCursorTransaction.Side side,
            Map<Integer, ItemStack> items
    ) {
        private DragTarget {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(items, "items");
        }
    }
}
