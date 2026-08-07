package nl.hauntedmc.serverfeatures.features.economy.service;

/** Signals bounded-admission rejection or timeout without claiming that a mutation was rejected. */
final class EconomyOverloadedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    EconomyOverloadedException(String message) {
        super(message);
    }

    EconomyOverloadedException(String message, Throwable cause) {
        super(message, cause);
    }
}
