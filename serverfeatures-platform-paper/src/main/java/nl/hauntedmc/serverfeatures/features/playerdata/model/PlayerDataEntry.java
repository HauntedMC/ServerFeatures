package nl.hauntedmc.serverfeatures.features.playerdata.model;

import java.util.Objects;

public record PlayerDataEntry(String key, String type, String value) {

    public PlayerDataEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
