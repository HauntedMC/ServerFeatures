package nl.hauntedmc.serverfeatures.features.invtools.gui;

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Tracks cursor custody for an editable offline InvTools view.
 *
 * <p>The cursor remains detached from both persistence domains until an item is placed. A transfer is
 * journaled only when cursor contents cross from the staff inventory to the target snapshot or in the
 * opposite direction. Combining a non-empty cursor with a similar stack from the other domain is
 * rejected because that would mix two rollback owners in one Bukkit stack.</p>
 */
public final class OfflineCursorTransaction {

    private ItemStack cursor;
    private Side owner;

    public OfflineCursorTransaction() {
        this(null, null);
    }

    public OfflineCursorTransaction(ItemStack cursor, Side owner) {
        this.cursor = cloneOrNull(cursor);
        this.owner = this.cursor == null ? null : Objects.requireNonNull(owner, "owner");
    }

    public Optional<Plan> plan(Side side, InventoryAction action, ItemStack slotItem) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(action, "action");

        ItemStack currentCursor = cursor();
        Optional<InventoryClickMutation.Result> mutation = InventoryClickMutation.apply(
                action,
                slotItem,
                currentCursor
        );
        if (mutation.isEmpty()) {
            return Optional.empty();
        }

        InventoryClickMutation.Result result = mutation.get();
        Movement movement = movement(action, slotItem, currentCursor, result);
        if (movement.slotToCursor() != null
                && currentCursor != null
                && owner != side
                && movement.cursorToSlot() == null) {
            return Optional.empty();
        }

        Transfer transfer = null;
        if (movement.cursorToSlot() != null && owner != null && owner != side) {
            transfer = new Transfer(
                    movement.cursorToSlot(),
                    owner == Side.TARGET && side == Side.VIEWER
            );
        }

        Side nextOwner;
        if (result.cursorItem() == null) {
            nextOwner = null;
        } else if (action == InventoryAction.SWAP_WITH_CURSOR) {
            nextOwner = side;
        } else if (currentCursor == null && movement.slotToCursor() != null) {
            nextOwner = side;
        } else {
            nextOwner = owner;
        }
        return Optional.of(new Plan(result, nextOwner, transfer));
    }

    public void commit(Plan plan) {
        Plan checked = Objects.requireNonNull(plan, "plan");
        cursor = checked.result().cursorItem();
        owner = cursor == null ? null : Objects.requireNonNull(checked.nextOwner(), "nextOwner");
    }

    public void replaceAfterSameSideDrag(ItemStack changedCursor, Side side) {
        cursor = cloneOrNull(changedCursor);
        owner = cursor == null ? null : Objects.requireNonNull(side, "side");
    }

    public ItemStack cursor() {
        return cloneOrNull(cursor);
    }

    public Side owner() {
        return owner;
    }

    public boolean hasCursor() {
        return cursor != null;
    }

    public void clear() {
        cursor = null;
        owner = null;
    }

    private static Movement movement(
            InventoryAction action,
            ItemStack slotBefore,
            ItemStack cursorBefore,
            InventoryClickMutation.Result result
    ) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> new Movement(
                    difference(slotBefore, result.slotItem()),
                    null
            );
            case PLACE_ALL, PLACE_ONE, PLACE_SOME -> new Movement(
                    null,
                    difference(cursorBefore, result.cursorItem())
            );
            case SWAP_WITH_CURSOR -> new Movement(
                    cloneOrNull(slotBefore),
                    cloneOrNull(cursorBefore)
            );
            default -> new Movement(null, null);
        };
    }

    private static ItemStack difference(ItemStack before, ItemStack after) {
        ItemStack normalizedBefore = cloneOrNull(before);
        ItemStack normalizedAfter = cloneOrNull(after);
        if (normalizedBefore == null) {
            return null;
        }
        int afterAmount = normalizedAfter == null ? 0 : normalizedAfter.getAmount();
        int difference = normalizedBefore.getAmount() - afterAmount;
        return difference <= 0 ? null : withAmount(normalizedBefore, difference);
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
        return item == null || item.getType().isAir() || item.getAmount() <= 0
                ? null
                : item.clone();
    }

    public enum Side {
        VIEWER,
        TARGET
    }

    public record Transfer(ItemStack item, boolean addedToViewer) {
        public Transfer {
            item = Objects.requireNonNull(cloneOrNull(item), "item");
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }

    public record Plan(
            InventoryClickMutation.Result result,
            Side nextOwner,
            Transfer transfer
    ) {
        public Plan {
            Objects.requireNonNull(result, "result");
            if (result.cursorItem() != null) {
                Objects.requireNonNull(nextOwner, "nextOwner");
            }
        }
    }

    private record Movement(ItemStack slotToCursor, ItemStack cursorToSlot) {
        private Movement {
            slotToCursor = cloneOrNull(slotToCursor);
            cursorToSlot = cloneOrNull(cursorToSlot);
        }

        @Override
        public ItemStack slotToCursor() {
            return cloneOrNull(slotToCursor);
        }

        @Override
        public ItemStack cursorToSlot() {
            return cloneOrNull(cursorToSlot);
        }
    }
}
