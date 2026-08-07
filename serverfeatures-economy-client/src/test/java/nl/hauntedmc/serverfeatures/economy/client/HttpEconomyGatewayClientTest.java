package nl.hauntedmc.serverfeatures.economy.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpEconomyGatewayClientTest {

    @Test
    void refusesInsecureOrRelativeGatewayEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpEconomyGatewayClient(HttpClient.newHttpClient(), URI.create("http://localhost:8080"), () -> "token"));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpEconomyGatewayClient(HttpClient.newHttpClient(), URI.create("/economy"), () -> "token"));
    }
}
