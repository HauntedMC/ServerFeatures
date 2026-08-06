package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;

final class EconomyRejectedException extends RuntimeException {
    private final EconomyResultStatus status;

    EconomyRejectedException(EconomyResultStatus status, String message) {
        super(message);
        this.status = status;
    }

    EconomyResultStatus status() {
        return status;
    }
}
