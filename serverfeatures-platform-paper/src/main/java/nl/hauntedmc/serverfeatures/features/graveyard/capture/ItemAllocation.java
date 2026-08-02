package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public record ItemAllocation(int preferredSlot, ItemStack item) {
    public ItemAllocation {
        Objects.requireNonNull(item, "item");
        item = item.clone();
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
