package nl.hauntedmc.serverfeatures.features.vanish.internal;

import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.framework.port.VanishVisibilityPort;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Feature-local implementation of the runtime vanish visibility port. */
public final class VanishAPI implements VanishVisibilityPort {

    private final Vanish feature;

    public VanishAPI(Vanish feature) {
        this.feature = feature;
    }

    public Set<UUID> getVanishedPlayers() {
        return feature.getService().allVanished();
    }

    public int getVanishedCount() {
        return feature.getService().countVanished();
    }

    @Override
    public Set<UUID> vanishedPlayers() {
        return getVanishedPlayers();
    }

    @Override
    public int vanishedCount() {
        return getVanishedCount();
    }

    @Override
    public boolean isVanished(UUID uuid) {
        return feature.getService().isVanished(uuid);
    }

    @Override
    public CompletionStage<Boolean> resolveInitialVanishState(UUID uuid) {
        return feature.getService().awaitInitialVanishState(uuid);
    }
}
