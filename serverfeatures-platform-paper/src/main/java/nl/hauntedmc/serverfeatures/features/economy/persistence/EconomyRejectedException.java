package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;

/** Structured policy rejection that must be returned to API callers without being logged as an infrastructure failure. */
public final class EconomyRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final EconomyResultStatus status;

    EconomyRejectedException(EconomyResultStatus status, String message) {
        super(message);
        this.status = status;
    }

    public EconomyResultStatus status() {
        return status;
    }
}
