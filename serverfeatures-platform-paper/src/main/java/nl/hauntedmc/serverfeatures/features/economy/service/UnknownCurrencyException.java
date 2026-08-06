package nl.hauntedmc.serverfeatures.features.economy.service;

/** Signals that a caller requested an absent currency or a mismatched currency scope. */
final class UnknownCurrencyException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    UnknownCurrencyException(String message) {
        super(message);
    }

    UnknownCurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
