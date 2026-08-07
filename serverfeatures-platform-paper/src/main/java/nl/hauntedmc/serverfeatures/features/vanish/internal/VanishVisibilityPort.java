package nl.hauntedmc.serverfeatures.features.vanish.internal;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Runtime-only contract for features that need vanish-aware connection visibility. */
public interface VanishVisibilityPort {
    Set<UUID> vanishedPlayers();

    int vanishedCount();

    boolean isVanished(UUID playerUuid);

    CompletionStage<Boolean> resolveInitialVanishState(UUID playerUuid);
}
