package nl.hauntedmc.serverfeatures.features.invtools.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.Objects;

/**
 * Detached inventory state. Every item crossing this boundary is cloned so disk work and GUI
 * sessions never retain references owned by a live Bukkit inventory.
 */
public final class InventorySnapshot {

    public static final int STORAGE_SIZE = 36;
    public static final int ENDER_CHEST_SIZE = 27;
    public static final int BOOTS_SLOT = 100;
    public static final int LEGGINGS_SLOT = 101;
    public static final int CHESTPLATE_SLOT = 102;
    public static final int HELMET_SLOT = 103;
    public static final int OFF_HAND_SLOT = -106;

    private static final int[] PLAYER_BACKING_SLOTS = createPlayerBackingSlots();
    private static final int[] ENDER_BACKING_SLOTS = createEnderBackingSlots();

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
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        return new InventorySnapshot(
                inventory.getStorageContents(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots(),
                inventory.getItemInOffHand(),
                player.getEnderChest().getStorageContents()
        );
    }

    public InventorySnapshot withBackingSlot(InventoryKind kind, int backingSlot, ItemStack item) {
        Objects.requireNonNull(kind, "kind");
        if (kind == InventoryKind.ENDER_CHEST) {
            requireRange(backingSlot, 0, ENDER_CHEST_SIZE, "ender chest slot");
            ItemStack[] changed = enderChest();
            changed[backingSlot] = cloneItem(item);
            return new InventorySnapshot(
                    storage,
                    helmet,
                    chestplate,
                    leggings,
                    boots,
                    offHand,
                    changed
            );
        }

        if (backingSlot >= 0 && backingSlot < STORAGE_SIZE) {
            ItemStack[] changed = storage();
            changed[backingSlot] = cloneItem(item);
            return new InventorySnapshot(
                    changed,
                    helmet,
                    chestplate,
                    leggings,
                    boots,
                    offHand,
                    enderChest
            );
        }

        return switch (backingSlot) {
            case BOOTS_SLOT ->
                    new InventorySnapshot(storage, helmet, chestplate, leggings, item, offHand, enderChest);
            case LEGGINGS_SLOT ->
                    new InventorySnapshot(storage, helmet, chestplate, item, boots, offHand, enderChest);
            case CHESTPLATE_SLOT ->
                    new InventorySnapshot(storage, helmet, item, leggings, boots, offHand, enderChest);
            case HELMET_SLOT ->
                    new InventorySnapshot(storage, item, chestplate, leggings, boots, offHand, enderChest);
            case OFF_HAND_SLOT ->
                    new InventorySnapshot(storage, helmet, chestplate, leggings, boots, item, enderChest);
            default -> throw new IllegalArgumentException("Unsupported player inventory slot: " + backingSlot);
        };
    }

    public ItemStack itemAt(InventoryKind kind, int backingSlot) {
        Objects.requireNonNull(kind, "kind");
        return cloneItem(backingItemAt(kind, backingSlot));
    }

    private ItemStack backingItemAt(InventoryKind kind, int backingSlot) {
        if (kind == InventoryKind.ENDER_CHEST) {
            requireRange(backingSlot, 0, ENDER_CHEST_SIZE, "ender chest slot");
            return enderChest[backingSlot];
        }
        if (backingSlot >= 0 && backingSlot < STORAGE_SIZE) {
            return storage[backingSlot];
        }
        return switch (backingSlot) {
            case BOOTS_SLOT -> boots;
            case LEGGINGS_SLOT -> leggings;
            case CHESTPLATE_SLOT -> chestplate;
            case HELMET_SLOT -> helmet;
            case OFF_HAND_SLOT -> offHand;
            default -> throw new IllegalArgumentException("Unsupported player inventory slot: " + backingSlot);
        };
    }

    public int[] changedBackingSlots(InventoryKind kind, InventorySnapshot other) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(other, "other");
        int[] slots = kind == InventoryKind.PLAYER
                ? PLAYER_BACKING_SLOTS
                : ENDER_BACKING_SLOTS;
        int[] changed = new int[slots.length];
        int changedCount = 0;
        for (int slot : slots) {
            if (!sameItem(backingItemAt(kind, slot), other.backingItemAt(kind, slot))) {
                changed[changedCount++] = slot;
            }
        }
        return Arrays.copyOf(changed, changedCount);
    }

    /**
     * Inserts a carried stack back into this inventory without overwriting another item.
     *
     * <p>This is used to settle an offline editor's cursor before committing playerdata. A
     * correctly isolated edit session always has enough capacity because the carried items came
     * from the same snapshot.</p>
     */
    public InsertionResult insert(InventoryKind kind, ItemStack carriedItem) {
        Objects.requireNonNull(kind, "kind");
        ItemStack remainder = cloneItem(carriedItem);
        if (remainder == null) {
            return new InsertionResult(this, null);
        }

        int[] backingSlots = kind == InventoryKind.PLAYER
                ? PLAYER_BACKING_SLOTS
                : ENDER_BACKING_SLOTS;
        InventorySnapshot changed = this;

        for (int backingSlot : backingSlots) {
            ItemStack existing = changed.itemAt(kind, backingSlot);
            if (existing == null || !existing.isSimilar(remainder)) {
                continue;
            }
            int capacity = existing.getMaxStackSize() - existing.getAmount();
            if (capacity <= 0) {
                continue;
            }
            int transferred = Math.min(capacity, remainder.getAmount());
            changed = changed.withBackingSlot(
                    kind,
                    backingSlot,
                    withAmount(existing, existing.getAmount() + transferred)
            );
            remainder = withAmount(remainder, remainder.getAmount() - transferred);
            if (remainder == null) {
                return new InsertionResult(changed, null);
            }
        }

        for (int backingSlot : backingSlots) {
            if (changed.itemAt(kind, backingSlot) != null) {
                continue;
            }
            int transferred = Math.min(remainder.getMaxStackSize(), remainder.getAmount());
            changed = changed.withBackingSlot(kind, backingSlot, withAmount(remainder, transferred));
            remainder = withAmount(remainder, remainder.getAmount() - transferred);
            if (remainder == null) {
                return new InsertionResult(changed, null);
            }
        }
        return new InsertionResult(changed, remainder);
    }

    public void apply(InventoryKind kind, Player player) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(player, "player");
        if (kind == InventoryKind.ENDER_CHEST) {
            player.getEnderChest().setStorageContents(enderChest());
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(storage());
        inventory.setHelmet(helmet());
        inventory.setChestplate(chestplate());
        inventory.setLeggings(leggings());
        inventory.setBoots(boots());
        inventory.setItemInOffHand(offHand());
        player.updateInventory();
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

    private static ItemStack[] cloneArray(ItemStack[] source, int expectedSize, String name) {
        Objects.requireNonNull(source, name);
        if (source.length != expectedSize) {
            throw new IllegalArgumentException(name + " must contain " + expectedSize + " slots");
        }
        return Arrays.stream(source).map(InventorySnapshot::cloneItem).toArray(ItemStack[]::new);
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone();
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) {
            return null;
        }
        ItemStack changed = item.clone();
        changed.setAmount(amount);
        return changed;
    }

    private static int[] createPlayerBackingSlots() {
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

    private static int[] createEnderBackingSlots() {
        int[] slots = new int[ENDER_CHEST_SIZE];
        for (int slot = 0; slot < ENDER_CHEST_SIZE; slot++) {
            slots[slot] = slot;
        }
        return slots;
    }

    private static void requireRange(int value, int minimum, int maximumExclusive, String name) {
        if (value < minimum || value >= maximumExclusive) {
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
