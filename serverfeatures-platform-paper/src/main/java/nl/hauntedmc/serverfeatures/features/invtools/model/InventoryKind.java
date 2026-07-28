package nl.hauntedmc.serverfeatures.features.invtools.model;

public enum InventoryKind {
    PLAYER("invsee"),
    ENDER_CHEST("endersee");

    private final String commandName;

    InventoryKind(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }
}
