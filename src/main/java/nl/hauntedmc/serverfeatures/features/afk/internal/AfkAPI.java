package nl.hauntedmc.serverfeatures.features.afk.internal;

import nl.hauntedmc.serverfeatures.features.afk.AFK;

import java.util.UUID;

/**
 * Simple public API to query AFK state.
 */
public class AfkAPI {

    private final AFK feature;

    public AfkAPI(AFK feature) {
        this.feature = feature;
    }

    /**
     * Returns whether the given player (by UUID) is currently AFK.
     */
    public boolean isAfk(UUID uuid) {
        return feature.getService().isAfk(uuid);
    }
}