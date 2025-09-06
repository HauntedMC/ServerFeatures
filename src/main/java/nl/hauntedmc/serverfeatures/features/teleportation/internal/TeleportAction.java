package nl.hauntedmc.serverfeatures.features.teleportation.internal;

public enum TeleportAction {
    RANDOM_TP("randomtp"),
    TP_POS("tppos");

    private final String configKey;

    TeleportAction(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
