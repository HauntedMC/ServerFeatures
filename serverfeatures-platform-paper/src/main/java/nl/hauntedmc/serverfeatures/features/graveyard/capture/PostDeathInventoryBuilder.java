package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Reconstructs the exact retained post-death inventory without overwriting slots or silently losing
 * plugin-added retained items. If the final retained set cannot be represented safely, capture is
 * abandoned and Bukkit keeps control of the original death event.
 */
public final class PostDeathInventoryBuilder {
    private final DeathDropMatcher matcher = new DeathDropMatcher();

    public PlayerInventoryState build(DeathInventorySnapshot snapshot, List<ItemStack> itemsToKeep) {
        PlayerInventoryState result = new PlayerInventoryState();
        List<ItemAllocation> allocations = matcher.allocate(snapshot.inventory().occupiedSlots(), itemsToKeep);
        for (ItemAllocation allocation : allocations) {
            ItemStack remainder = allocation.item().clone();
            placePreferred(result, allocation.preferredSlot(), remainder);
            mergeExistingStorage(result, remainder);
            fillEmptyStorage(result, remainder);
            if (remainder.getAmount() > 0) {
                throw new IllegalStateException("Retained death items do not fit in the player inventory");
            }
        }
        return result;
    }

    private static void placePreferred(PlayerInventoryState state, int slot, ItemStack remainder) {
        if (remainder.getAmount() <= 0 || !isLegalPreferredSlot(slot, remainder)) {
            return;
        }
        ItemStack current = state.get(slot);
        if (current == null) {
            int moved = Math.min(remainder.getAmount(), remainder.getMaxStackSize());
            ItemStack inserted = remainder.clone();
            inserted.setAmount(moved);
            state.set(slot, inserted);
            remainder.setAmount(remainder.getAmount() - moved);
            return;
        }
        merge(current, remainder);
        state.set(slot, current);
    }

    private static void mergeExistingStorage(PlayerInventoryState state, ItemStack remainder) {
        for (int slot = 0; slot < 36 && remainder.getAmount() > 0; slot++) {
            ItemStack current = state.get(slot);
            if (current == null || !current.isSimilar(remainder)) {
                continue;
            }
            merge(current, remainder);
            state.set(slot, current);
        }
    }

    private static void fillEmptyStorage(PlayerInventoryState state, ItemStack remainder) {
        while (remainder.getAmount() > 0) {
            int empty = state.firstEmptyStorageSlot();
            if (empty < 0) {
                return;
            }
            int moved = Math.min(remainder.getAmount(), remainder.getMaxStackSize());
            ItemStack inserted = remainder.clone();
            inserted.setAmount(moved);
            state.set(empty, inserted);
            remainder.setAmount(remainder.getAmount() - moved);
        }
    }

    private static void merge(ItemStack target, ItemStack remainder) {
        if (!target.isSimilar(remainder)) {
            return;
        }
        int capacity = Math.max(0, target.getMaxStackSize() - target.getAmount());
        int moved = Math.min(capacity, remainder.getAmount());
        target.setAmount(target.getAmount() + moved);
        remainder.setAmount(remainder.getAmount() - moved);
    }

    private static boolean isLegalPreferredSlot(int slot, ItemStack item) {
        if (InventorySlot.isStorage(slot) || slot == InventorySlot.OFF_HAND) {
            return true;
        }
        EquipmentSlot required = switch (slot) {
            case InventorySlot.BOOTS -> EquipmentSlot.FEET;
            case InventorySlot.LEGGINGS -> EquipmentSlot.LEGS;
            case InventorySlot.CHESTPLATE -> EquipmentSlot.CHEST;
            case InventorySlot.HELMET -> EquipmentSlot.HEAD;
            default -> null;
        };
        return required != null && item.getType().getEquipmentSlot() == required;
    }
}
