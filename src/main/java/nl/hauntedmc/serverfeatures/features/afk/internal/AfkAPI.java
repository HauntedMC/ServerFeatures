package nl.hauntedmc.serverfeatures.features.afk.internal;

import nl.hauntedmc.serverfeatures.features.afk.AFK;

import java.util.UUID;

public class AfkAPI {
    private final AFK feature;
    public AfkAPI(AFK feature) { this.feature = feature; }
    public boolean isAfk(UUID uuid) { return feature.getService().isAfk(uuid); }
}
