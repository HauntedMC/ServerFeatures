package nl.hauntedmc.serverfeatures.features.graveyard.capture;

public final class InventorySlot {
    public static final int BOOTS = 36;
    public static final int LEGGINGS = 37;
    public static final int CHESTPLATE = 38;
    public static final int HELMET = 39;
    public static final int OFF_HAND = 40;
    public static final int UNASSIGNED = -1;

    private InventorySlot() {
    }

    public static boolean isStorage(int slot) {
        return slot >= 0 && slot < 36;
    }
}
