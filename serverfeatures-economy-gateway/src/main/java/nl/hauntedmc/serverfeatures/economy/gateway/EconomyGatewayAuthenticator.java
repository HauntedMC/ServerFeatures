package nl.hauntedmc.serverfeatures.economy.gateway;

import java.util.Optional;

/**
 * Deployment-owned authentication boundary. Implementations should validate service credentials
 * and, where configured, the mutually authenticated TLS peer from the SSL session.
 */
@FunctionalInterface
public interface EconomyGatewayAuthenticator {
    Optional<EconomyGatewayPrincipal> authenticate(EconomyGatewayRequestContext request);
}
