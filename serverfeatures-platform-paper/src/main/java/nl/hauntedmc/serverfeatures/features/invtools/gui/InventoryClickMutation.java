package nl.hauntedmc.serverfeatures.features.invtools.gui;

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic direct-click semantics for mapped InvTools slots. Complex bulk actions are
 * deliberately rejected because Bukkit may otherwise route them through decorative GUI slots.
 */
public final class InventoryClickMutation {

    private InventoryClickMutation() {
    }

    public static Optional<Result> apply(
            InventoryAction action,
            ItemStack currentItem,
            ItemStack cursorItem
    ) {
        Objects.requireNonNull(action, "action");
        ItemStack current = cloneOrNull(currentItem);
        ItemStack cursor = cloneOrNull(cursorItem);

        return switch (action) {
            case PICKUP_ALL -> pickupAll(current, cursor);
            case PICKUP_HALF -> pickupHalf(current);
            case PICKUP_ONE -> pickupOne(current, cursor);
            case PICKUP_SOME -> pickupSome(current, cursor);
            case PLACE_ALL -> placeAll(current, cursor);
            case PLACE_ONE -> placeOne(current, cursor);
            case PLACE_SOME -> placeSome(current, cursor);
            case SWAP_WITH_CURSOR -> result(cursor, current);
            case DROP_ALL_SLOT -> current == null
                    ? Optional.empty()
                    : result(null, cursor);
            case DROP_ONE_SLOT -> current == null
                    ? Optional.empty()
                    : result(withAmount(current, current.getAmount() - 1), cursor);
            default -> Optional.empty();
        };
    }

    private static Optional<Result> pickupAll(ItemStack current, ItemStack cursor) {
        if (current == null) {
            return Optional.empty();
        }
        if (cursor == null) {
            return result(null, current);
        }
        if (!current.isSimilar(cursor)) {
            return Optional.empty();
        }
        int capacity = cursor.getMaxStackSize() - cursor.getAmount();
        if (capacity < current.getAmount()) {
            return Optional.empty();
        }
        return result(null, withAmount(cursor, cursor.getAmount() + current.getAmount()));
    }

    private static Optional<Result> pickupHalf(ItemStack current) {
        if (current == null) {
            return Optional.empty();
        }
        int pickedUp = (current.getAmount() + 1) / 2;
        ItemStack cursor = withAmount(current, pickedUp);
        ItemStack remaining = withAmount(current, current.getAmount() - pickedUp);
        return result(remaining, cursor);
    }

    private static Optional<Result> pickupOne(ItemStack current, ItemStack cursor) {
        if (current == null) {
            return Optional.empty();
        }
        if (cursor == null) {
            return result(withAmount(current, current.getAmount() - 1), withAmount(current, 1));
        }
        if (!current.isSimilar(cursor) || cursor.getAmount() >= cursor.getMaxStackSize()) {
            return Optional.empty();
        }
        return result(
                withAmount(current, current.getAmount() - 1),
                withAmount(cursor, cursor.getAmount() + 1)
        );
    }

    private static Optional<Result> pickupSome(ItemStack current, ItemStack cursor) {
        if (current == null || cursor == null || !current.isSimilar(cursor)) {
            return Optional.empty();
        }
        int capacity = cursor.getMaxStackSize() - cursor.getAmount();
        int transferred = Math.min(capacity, current.getAmount());
        if (transferred <= 0) {
            return Optional.empty();
        }
        return result(
                withAmount(current, current.getAmount() - transferred),
                withAmount(cursor, cursor.getAmount() + transferred)
        );
    }

    private static Optional<Result> placeOne(ItemStack current, ItemStack cursor) {
        if (cursor == null) {
            return Optional.empty();
        }
        if (current == null) {
            return result(withAmount(cursor, 1), withAmount(cursor, cursor.getAmount() - 1));
        }
        if (!current.isSimilar(cursor) || current.getAmount() >= current.getMaxStackSize()) {
            return Optional.empty();
        }
        return result(
                withAmount(current, current.getAmount() + 1),
                withAmount(cursor, cursor.getAmount() - 1)
        );
    }

    private static Optional<Result> placeAll(ItemStack current, ItemStack cursor) {
        if (cursor == null) {
            return Optional.empty();
        }
        if (current == null) {
            return result(cursor, null);
        }
        if (!current.isSimilar(cursor)) {
            return Optional.empty();
        }
        int capacity = current.getMaxStackSize() - current.getAmount();
        if (capacity < cursor.getAmount()) {
            return Optional.empty();
        }
        return result(withAmount(current, current.getAmount() + cursor.getAmount()), null);
    }

    private static Optional<Result> placeSome(ItemStack current, ItemStack cursor) {
        if (current == null || cursor == null || !current.isSimilar(cursor)) {
            return Optional.empty();
        }
        int capacity = current.getMaxStackSize() - current.getAmount();
        int transferred = Math.min(capacity, cursor.getAmount());
        if (transferred <= 0) {
            return Optional.empty();
        }
        return result(
                withAmount(current, current.getAmount() + transferred),
                withAmount(cursor, cursor.getAmount() - transferred)
        );
    }

    private static Optional<Result> result(ItemStack slot, ItemStack cursor) {
        return Optional.of(new Result(cloneOrNull(slot), cloneOrNull(cursor)));
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
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    public record Result(ItemStack slotItem, ItemStack cursorItem) {
        public Result {
            slotItem = cloneOrNull(slotItem);
            cursorItem = cloneOrNull(cursorItem);
        }

        @Override
        public ItemStack slotItem() {
            return cloneOrNull(slotItem);
        }

        @Override
        public ItemStack cursorItem() {
            return cloneOrNull(cursorItem);
        }
    }
}
