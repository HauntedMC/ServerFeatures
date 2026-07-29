package nl.hauntedmc.serverfeatures.features.invtools.model;

public enum InventoryKind {
    PLAYER("inventory"),
    ENDER_CHEST("enderchest");

    private final String commandSegment;

    InventoryKind(String commandSegment) {
        this.commandSegment = commandSegment;
    }

    public String commandSegment() {
        return commandSegment;
    }
}
