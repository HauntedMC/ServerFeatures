package nl.hauntedmc.serverfeatures.economy.client;

/** Non-success response from the authenticated Economy gateway. */
public final class EconomyGatewayException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public EconomyGatewayException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
