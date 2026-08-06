package nl.hauntedmc.serverfeatures.features.economy.service;

/** Signals that DataRegistry could not establish a canonical player identity. */
final class UnknownPlayerException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    UnknownPlayerException(String message) {
        super(message);
    }
}
