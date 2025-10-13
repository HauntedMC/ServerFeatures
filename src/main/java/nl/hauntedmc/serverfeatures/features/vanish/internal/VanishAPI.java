package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.serverfeatures.features.vanish.Vanish;

import java.util.Set;
import java.util.UUID;

/**
 * Public API other features can use to query vanish-aware player stats.
 */
public class VanishAPI {

    private final Vanish feature;

    public VanishAPI(Vanish feature) {
        this.feature = feature;
    }

    /**
     * List of currently vanished online players.
     */
    public Set<UUID> getVanishedPlayers() {
        return feature.getService().allVanished();
    }

    /**
     * Number of currently vanished online players.
     */
    public int getVanishedCount() {
        return feature.getService().countVanished();
    }

    public boolean isVanished(UUID uuid) {
        return feature.getService().isVanished(uuid);
    }
}
