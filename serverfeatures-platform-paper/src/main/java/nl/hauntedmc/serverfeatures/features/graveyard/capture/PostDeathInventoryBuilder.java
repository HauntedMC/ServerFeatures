package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class PostDeathInventoryBuilder {
    private final DeathDropMatcher matcher = new DeathDropMatcher();

    public PlayerInventoryState build(DeathInventorySnapshot snapshot, List<ItemStack> itemsToKeep) {
        PlayerInventoryState result = new PlayerInventoryState();
        List<ItemAllocation> allocations = matcher.allocate(snapshot.inventory().occupiedSlots(), itemsToKeep);
        for (ItemAllocation allocation : allocations) {
            if (allocation.preferredSlot() >= 0 && result.get(allocation.preferredSlot()) == null) {
                result.set(allocation.preferredSlot(), allocation.item());
                continue;
            }
            int empty = result.firstEmptyStorageSlot();
            if (empty >= 0) {
                result.set(empty, allocation.item());
            }
        }
        return result;
    }
}
