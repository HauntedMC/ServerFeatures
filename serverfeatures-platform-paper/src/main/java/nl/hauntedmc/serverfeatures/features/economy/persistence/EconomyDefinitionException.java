package nl.hauntedmc.serverfeatures.features.economy.persistence;

/** A single currency cannot safely use the persisted immutable definition. */
public final class EconomyDefinitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    EconomyDefinitionException(String message) {
        super(message);
    }

    EconomyDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
