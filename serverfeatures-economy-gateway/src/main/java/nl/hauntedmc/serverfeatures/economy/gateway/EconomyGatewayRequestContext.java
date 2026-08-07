package nl.hauntedmc.serverfeatures.economy.gateway;

import com.sun.net.httpserver.Headers;

import javax.net.ssl.SSLSession;
import java.util.Objects;

/** TLS connection and request metadata supplied to a deployment-specific gateway authenticator. */
public record EconomyGatewayRequestContext(Headers headers, SSLSession sslSession) {
    public EconomyGatewayRequestContext {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(sslSession, "sslSession");
    }
}
