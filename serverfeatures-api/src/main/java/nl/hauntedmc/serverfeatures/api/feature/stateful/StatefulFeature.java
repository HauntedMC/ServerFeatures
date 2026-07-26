package nl.hauntedmc.serverfeatures.api.feature.stateful;

import java.util.Optional;

/**
 * Optional contract for features that preserve transient runtime state across a full reload.
 *
 * @param <S> reload-state payload type owned by the feature
 */
public interface StatefulFeature<S extends SnapshotState> {

    /**
     * Captures transient state before the framework tears down the current feature instance.
     */
    Optional<S> captureReloadState();

    /**
     * Restores state after the replacement feature instance has initialized.
     */
    void restoreReloadState(S state);
}
