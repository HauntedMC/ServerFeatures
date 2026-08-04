package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministically associates final death-drop quantities with their original inventory slots.
 */
public final class DeathDropMatcher {
    public List<ItemAllocation> allocate(List<SlotStack> sourceSlots, List<ItemStack> requestedItems) {
        List<MutableSource> sources = sourceSlots.stream().map(MutableSource::new).toList();
        List<ItemAllocation> allocations = new ArrayList<>();

        for (ItemStack requested : requestedItems) {
            if (isEmpty(requested)) {
                continue;
            }
            int remaining = requested.getAmount();
            for (MutableSource source : sources) {
                if (remaining == 0) {
                    break;
                }
                if (source.remaining <= 0 || !source.item.isSimilar(requested)) {
                    continue;
                }
                int amount = Math.min(remaining, source.remaining);
                ItemStack allocated = requested.clone();
                allocated.setAmount(amount);
                allocations.add(new ItemAllocation(source.slot, allocated));
                source.remaining -= amount;
                remaining -= amount;
            }
            if (remaining > 0) {
                ItemStack unassigned = requested.clone();
                unassigned.setAmount(remaining);
                allocations.add(new ItemAllocation(InventorySlot.UNASSIGNED, unassigned));
            }
        }
        return List.copyOf(allocations);
    }

    private static boolean isEmpty(ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return true;
        }
        Material material = item.getType();
        return material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    private static final class MutableSource {
        private final int slot;
        private final ItemStack item;
        private int remaining;

        private MutableSource(SlotStack source) {
            slot = source.slot();
            item = source.item();
            remaining = item.getAmount();
        }
    }
}
