package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.serverfeatures.features.vanish.Vanish;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public API other features can use to query vanish-aware player state.
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

    /**
     * Completes after Vanish has resolved and applied the initial state for the active connection.
     *
     * <p>The completion is fenced to the current player session. Reconnects, disconnects and explicit
     * state changes invalidate stale persistence results.</p>
     */
    public CompletionStage<Boolean> resolveInitialVanishState(UUID uuid) {
        return feature.getService().awaitInitialVanishState(uuid);
    }
}
