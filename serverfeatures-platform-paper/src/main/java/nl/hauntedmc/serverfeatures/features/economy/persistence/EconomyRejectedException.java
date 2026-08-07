package nl.hauntedmc.serverfeatures.features.economy.persistence;

import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;

/**
 * Structured policy rejection returned to API callers without infrastructure-error logging.
 *
 * <p>DataProvider logs every {@link Exception} escaping a transaction, even when it is an
 * expected business rejection. It rolls back {@link Error}s without logging them; Economy catches
 * this controlled signal at its repository boundary and turns it into an {@code EconomyResult}.
 * No {@code Error} leaves the Economy boundary.</p>
 */
public final class EconomyRejectedException extends Error {
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
