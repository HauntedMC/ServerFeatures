package nl.hauntedmc.serverfeatures.features.invtools.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.Objects;

/** Detached inventory state with cloned items at every ownership boundary. */
public final class InventorySnapshot {

    public static final int STORAGE_SIZE = 36;
    public static final int ENDER_CHEST_SIZE = 27;
    public static final int BOOTS_SLOT = 100;
    public static final int LEGGINGS_SLOT = 101;
    public static final int CHESTPLATE_SLOT = 102;
    public static final int HELMET_SLOT = 103;
    public static final int OFF_HAND_SLOT = -106;

    private static final int NO_EQUIPMENT_SLOT = Integer.MIN_VALUE;
    private static final int[] PLAYER_BACKING_SLOTS = playerBackingSlots();
    private static final int[] PLAYER_SHIFT_SLOTS = playerShiftSlots();
    private static final int[] ENDER_SLOTS = sequentialSlots(ENDER_CHEST_SIZE);

    private final ItemStack[] storage;
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;
    private final ItemStack offHand;
    private final ItemStack[] enderChest;

    public InventorySnapshot(
            ItemStack[] storage,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offHand,
            ItemStack[] enderChest
    ) {
        this.storage = cloneArray(storage, STORAGE_SIZE, "storage");
        this.helmet = cloneItem(helmet);
        this.chestplate = cloneItem(chestplate);
        this.leggings = cloneItem(leggings);
        this.boots = cloneItem(boots);
        this.offHand = cloneItem(offHand);
        this.enderChest = cloneArray(enderChest, ENDER_CHEST_SIZE, "enderChest");
    }

    public static InventorySnapshot empty() {
        return new InventorySnapshot(
                new ItemStack[STORAGE_SIZE],
                null,
                null,
                null,
                null,
                null,
                new ItemStack[ENDER_CHEST_SIZE]
        );
    }

    public static InventorySnapshot capture(Player player) {
        Player checked = Objects.requireNonNull(player, "player");
        PlayerInventory inventory = checked.getInventory();
        return new InventorySnapshot(
                inventory.getStorageContents(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots(),
                inventory.getItemInOffHand(),
                checked.getEnderChest().getStorageContents()
        );
    }

    public InventorySnapshot withBackingSlot(InventoryKind kind, int slot, ItemStack item) {
        Objects.requireNonNull(kind, "kind");
        if (kind == InventoryKind.ENDER_CHEST) {
            requireRange(slot, ENDER_CHEST_SIZE, "ender chest slot");
            ItemStack[] changed = enderChest();
            changed[slot] = cloneItem(item);
            return copy(storage, helmet, chestplate, leggings, boots, offHand, changed);
        }
        if (slot >= 0 && slot < STORAGE_SIZE) {
            ItemStack[] changed = storage();
            changed[slot] = cloneItem(item);
            return copy(changed, helmet, chestplate, leggings, boots, offHand, enderChest);
        }
        return switch (slot) {
            case HELMET_SLOT -> copy(storage, item, chestplate, leggings, boots, offHand, enderChest);
            case CHESTPLATE_SLOT -> copy(storage, helmet, item, leggings, boots, offHand, enderChest);
            case LEGGINGS_SLOT -> copy(storage, helmet, chestplate, item, boots, offHand, enderChest);
            case BOOTS_SLOT -> copy(storage, helmet, chestplate, leggings, item, offHand, enderChest);
            case OFF_HAND_SLOT -> copy(storage, helmet, chestplate, leggings, boots, item, enderChest);
            default -> throw new IllegalArgumentException("Unsupported player inventory slot: " + slot);
        };
    }

    public ItemStack itemAt(InventoryKind kind, int slot) {
        return cloneItem(backingItemAt(Objects.requireNonNull(kind, "kind"), slot));
    }

    private ItemStack backingItemAt(InventoryKind kind, int slot) {
        if (kind == InventoryKind.ENDER_CHEST) {
            requireRange(slot, ENDER_CHEST_SIZE, "ender chest slot");
            return enderChest[slot];
        }
        if (slot >= 0 && slot < STORAGE_SIZE) {
            return storage[slot];
        }
        return switch (slot) {
            case HELMET_SLOT -> helmet;
            case CHESTPLATE_SLOT -> chestplate;
            case LEGGINGS_SLOT -> leggings;
            case BOOTS_SLOT -> boots;
            case OFF_HAND_SLOT -> offHand;
            default -> throw new IllegalArgumentException("Unsupported player inventory slot: " + slot);
        };
    }

    public int[] changedBackingSlots(InventoryKind kind, InventorySnapshot other) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(other, "other");
        int[] candidates = kind == InventoryKind.PLAYER ? PLAYER_BACKING_SLOTS : ENDER_SLOTS;
        int[] changed = new int[candidates.length];
        int count = 0;
        for (int slot : candidates) {
            if (!sameItem(backingItemAt(kind, slot), other.backingItemAt(kind, slot))) {
                changed[count++] = slot;
            }
        }
        return Arrays.copyOf(changed, count);
    }

    /** Restores a cursor stack to any compatible slot without overwriting another item. */
    public InsertionResult insert(InventoryKind kind, ItemStack carriedItem) {
        Objects.requireNonNull(kind, "kind");
        return insertIntoSlots(
                kind,
                carriedItem,
                kind == InventoryKind.PLAYER ? PLAYER_BACKING_SLOTS : ENDER_SLOTS
        );
    }

    /**
     * Shift-inserts with normal player expectations: empty matching armor slot first, then main
     * storage, then hotbar. Ordinary items never probe armor or offhand slots.
     */
    public InsertionResult shiftInsert(InventoryKind kind, ItemStack carriedItem) {
        Objects.requireNonNull(kind, "kind");
        if (kind == InventoryKind.ENDER_CHEST) {
            return insertIntoSlots(kind, carriedItem, ENDER_SLOTS);
        }
        ItemStack remainder = cloneItem(carriedItem);
        if (remainder == null) {
            return new InsertionResult(this, null);
        }

        InventorySnapshot changed = this;
        int equipmentSlot = preferredEquipmentSlot(remainder);
        if (equipmentSlot != NO_EQUIPMENT_SLOT
                && changed.itemAt(InventoryKind.PLAYER, equipmentSlot) == null) {
            changed = changed.withBackingSlot(
                    InventoryKind.PLAYER,
                    equipmentSlot,
                    withAmount(remainder, 1)
            );
            remainder = withAmount(remainder, remainder.getAmount() - 1);
        }
        return remainder == null
                ? new InsertionResult(changed, null)
                : changed.insertIntoSlots(InventoryKind.PLAYER, remainder, PLAYER_SHIFT_SLOTS);
    }

    private InsertionResult insertIntoSlots(
            InventoryKind kind,
            ItemStack carriedItem,
            int[] slots
    ) {
        ItemStack remainder = cloneItem(carriedItem);
        if (remainder == null) {
            return new InsertionResult(this, null);
        }
        InventorySnapshot changed = this;

        for (int slot : slots) {
            ItemStack existing = changed.itemAt(kind, slot);
            if (existing == null || !existing.isSimilar(remainder)) {
                continue;
            }
            int transferred = Math.min(
                    existing.getMaxStackSize() - existing.getAmount(),
                    remainder.getAmount()
            );
            if (transferred <= 0) {
                continue;
            }
            changed = changed.withBackingSlot(
                    kind,
                    slot,
                    withAmount(existing, existing.getAmount() + transferred)
            );
            remainder = withAmount(remainder, remainder.getAmount() - transferred);
            if (remainder == null) {
                return new InsertionResult(changed, null);
            }
        }

        for (int slot : slots) {
            if (changed.itemAt(kind, slot) != null || !allowsItemInSlot(kind, slot, remainder)) {
                continue;
            }
            int transferred = Math.min(remainder.getMaxStackSize(), remainder.getAmount());
            changed = changed.withBackingSlot(kind, slot, withAmount(remainder, transferred));
            remainder = withAmount(remainder, remainder.getAmount() - transferred);
            if (remainder == null) {
                return new InsertionResult(changed, null);
            }
        }
        return new InsertionResult(changed, remainder);
    }

    public ItemStack[] storage() {
        return cloneArray(storage, STORAGE_SIZE, "storage");
    }

    public ItemStack helmet() {
        return cloneItem(helmet);
    }

    public ItemStack chestplate() {
        return cloneItem(chestplate);
    }

    public ItemStack leggings() {
        return cloneItem(leggings);
    }

    public ItemStack boots() {
        return cloneItem(boots);
    }

    public ItemStack offHand() {
        return cloneItem(offHand);
    }

    public ItemStack[] enderChest() {
        return cloneArray(enderChest, ENDER_CHEST_SIZE, "enderChest");
    }

    private static InventorySnapshot copy(
            ItemStack[] storage,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offHand,
            ItemStack[] enderChest
    ) {
        return new InventorySnapshot(
                storage,
                helmet,
                chestplate,
                leggings,
                boots,
                offHand,
                enderChest
        );
    }

    private static ItemStack[] cloneArray(ItemStack[] source, int size, String name) {
        Objects.requireNonNull(source, name);
        if (source.length != size) {
            throw new IllegalArgumentException(name + " must contain " + size + " slots");
        }
        return Arrays.stream(source).map(InventorySnapshot::cloneItem).toArray(ItemStack[]::new);
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0
                ? null
                : item.clone();
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static boolean allowsItemInSlot(InventoryKind kind, int slot, ItemStack item) {
        if (kind != InventoryKind.PLAYER || item == null) {
            return true;
        }
        EquipmentSlot required = switch (slot) {
            case HELMET_SLOT -> EquipmentSlot.HEAD;
            case CHESTPLATE_SLOT -> EquipmentSlot.CHEST;
            case LEGGINGS_SLOT -> EquipmentSlot.LEGS;
            case BOOTS_SLOT -> EquipmentSlot.FEET;
            default -> null;
        };
        return required == null || item.getType().getEquipmentSlot() == required;
    }

    private static int preferredEquipmentSlot(ItemStack item) {
        EquipmentSlot equipmentSlot = item.getType().getEquipmentSlot();
        if (equipmentSlot == null) {
            return NO_EQUIPMENT_SLOT;
        }
        return switch (equipmentSlot) {
            case HEAD -> HELMET_SLOT;
            case CHEST -> CHESTPLATE_SLOT;
            case LEGS -> LEGGINGS_SLOT;
            case FEET -> BOOTS_SLOT;
            default -> NO_EQUIPMENT_SLOT;
        };
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return null;
        }
        ItemStack changed = item.clone();
        changed.setAmount(amount);
        return changed;
    }

    private static int[] playerBackingSlots() {
        int[] slots = new int[STORAGE_SIZE + 5];
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            slots[slot] = slot;
        }
        slots[STORAGE_SIZE] = HELMET_SLOT;
        slots[STORAGE_SIZE + 1] = CHESTPLATE_SLOT;
        slots[STORAGE_SIZE + 2] = LEGGINGS_SLOT;
        slots[STORAGE_SIZE + 3] = BOOTS_SLOT;
        slots[STORAGE_SIZE + 4] = OFF_HAND_SLOT;
        return slots;
    }

    private static int[] playerShiftSlots() {
        int[] slots = new int[STORAGE_SIZE];
        int index = 0;
        for (int slot = 9; slot < STORAGE_SIZE; slot++) {
            slots[index++] = slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            slots[index++] = slot;
        }
        return slots;
    }

    private static int[] sequentialSlots(int size) {
        int[] slots = new int[size];
        for (int slot = 0; slot < size; slot++) {
            slots[slot] = slot;
        }
        return slots;
    }

    private static void requireRange(int value, int maximumExclusive, String name) {
        if (value < 0 || value >= maximumExclusive) {
            throw new IllegalArgumentException(name + " is out of bounds: " + value);
        }
    }

    public record InsertionResult(InventorySnapshot snapshot, ItemStack remainder) {
        public InsertionResult {
            Objects.requireNonNull(snapshot, "snapshot");
            remainder = cloneItem(remainder);
        }

        @Override
        public ItemStack remainder() {
            return cloneItem(remainder);
        }
    }
}
