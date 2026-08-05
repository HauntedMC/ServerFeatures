package nl.hauntedmc.serverfeatures.features.fairperks.model;

public enum PerkType {
    FLY,
    GOD;

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
