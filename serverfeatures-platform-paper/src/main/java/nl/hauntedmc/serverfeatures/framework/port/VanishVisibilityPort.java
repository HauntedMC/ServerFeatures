package nl.hauntedmc.serverfeatures.framework.port;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Runtime-only framework port for vanish-aware connection visibility.
 *
 * <p>The contract lives in the framework rather than either participating feature so consumers
 * never depend on another feature's implementation package.</p>
 */
public interface VanishVisibilityPort {
    Set<UUID> vanishedPlayers();

    int vanishedCount();

    boolean isVanished(UUID playerUuid);

    CompletionStage<Boolean> resolveInitialVanishState(UUID playerUuid);
}
