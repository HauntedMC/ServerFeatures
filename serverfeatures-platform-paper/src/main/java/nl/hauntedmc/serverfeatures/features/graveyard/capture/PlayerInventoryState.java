package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;

public final class PlayerInventoryState {
    private final ItemStack[] storage;
    private ItemStack boots;
    private ItemStack leggings;
    private ItemStack chestplate;
    private ItemStack helmet;
    private ItemStack offHand;

    public PlayerInventoryState() {
        storage = new ItemStack[36];
    }

    private PlayerInventoryState(
            ItemStack[] storage,
            ItemStack boots,
            ItemStack leggings,
            ItemStack chestplate,
            ItemStack helmet,
            ItemStack offHand
    ) {
        this.storage = cloneArray(storage);
        this.boots = cloneOrNull(boots);
        this.leggings = cloneOrNull(leggings);
        this.chestplate = cloneOrNull(chestplate);
        this.helmet = cloneOrNull(helmet);
        this.offHand = cloneOrNull(offHand);
    }

    public static PlayerInventoryState capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new PlayerInventoryState(
                inventory.getStorageContents(),
                inventory.getBoots(),
                inventory.getLeggings(),
                inventory.getChestplate(),
                inventory.getHelmet(),
                inventory.getItemInOffHand()
        );
    }

    public ItemStack get(int slot) {
        if (InventorySlot.isStorage(slot)) {
            return cloneOrNull(storage[slot]);
        }
        return cloneOrNull(switch (slot) {
            case InventorySlot.BOOTS -> boots;
            case InventorySlot.LEGGINGS -> leggings;
            case InventorySlot.CHESTPLATE -> chestplate;
            case InventorySlot.HELMET -> helmet;
            case InventorySlot.OFF_HAND -> offHand;
            default -> null;
        });
    }

    public void set(int slot, ItemStack item) {
        if (InventorySlot.isStorage(slot)) {
            storage[slot] = cloneOrNull(item);
            return;
        }
        switch (slot) {
            case InventorySlot.BOOTS -> boots = cloneOrNull(item);
            case InventorySlot.LEGGINGS -> leggings = cloneOrNull(item);
            case InventorySlot.CHESTPLATE -> chestplate = cloneOrNull(item);
            case InventorySlot.HELMET -> helmet = cloneOrNull(item);
            case InventorySlot.OFF_HAND -> offHand = cloneOrNull(item);
            default -> throw new IllegalArgumentException("Unknown player inventory slot " + slot);
        }
    }

    public int firstEmptyStorageSlot() {
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    public ItemStack[] storageContents() {
        return cloneArray(storage);
    }

    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(storageContents());
        inventory.setBoots(cloneOrNull(boots));
        inventory.setLeggings(cloneOrNull(leggings));
        inventory.setChestplate(cloneOrNull(chestplate));
        inventory.setHelmet(cloneOrNull(helmet));
        inventory.setItemInOffHand(offHand == null ? ItemStack.empty() : offHand.clone());
    }

    public java.util.List<SlotStack> occupiedSlots() {
        java.util.List<SlotStack> slots = new java.util.ArrayList<>();
        for (int slot = 0; slot < storage.length; slot++) {
            add(slots, slot, storage[slot]);
        }
        add(slots, InventorySlot.BOOTS, boots);
        add(slots, InventorySlot.LEGGINGS, leggings);
        add(slots, InventorySlot.CHESTPLATE, chestplate);
        add(slots, InventorySlot.HELMET, helmet);
        add(slots, InventorySlot.OFF_HAND, offHand);
        return java.util.List.copyOf(slots);
    }

    public PlayerInventoryState copy() {
        return new PlayerInventoryState(storage, boots, leggings, chestplate, helmet, offHand);
    }

    private static void add(java.util.List<SlotStack> target, int slot, ItemStack item) {
        if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
            target.add(new SlotStack(slot, item));
        }
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        ItemStack[] copy = Arrays.copyOf(source, 36);
        for (int index = 0; index < copy.length; index++) {
            copy[index] = cloneOrNull(copy[index]);
        }
        return copy;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }
}
