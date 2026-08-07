package nl.hauntedmc.serverfeatures.economy.gateway;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyGatewayChargeRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Small TLS-only adapter exposing the durable Economy workflow API to authenticated services.
 * It has no database credentials and delegates every decision to {@link EconomyApi}.
 */
public final class EconomyGatewayServer implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int MAX_BODY_BYTES = 16_384;

    private final HttpsServer server;
    private final EconomyApi economy;
    private final EconomyGatewayAuthenticator authenticator;

    private EconomyGatewayServer(HttpsServer server, EconomyApi economy, EconomyGatewayAuthenticator authenticator) {
        this.server = Objects.requireNonNull(server, "server");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        server.createContext("/v1/economy/workflows/charge", this::charge);
        server.createContext("/v1/economy/workflows/status", this::status);
    }

    /** Creates a gateway which can only listen through the caller-provided HTTPS configuration. */
    public static EconomyGatewayServer create(InetSocketAddress address, int backlog, HttpsConfigurator tls,
                                              EconomyApi economy, EconomyGatewayAuthenticator authenticator) throws IOException {
        Objects.requireNonNull(address, "address");
        HttpsServer server = HttpsServer.create(address, backlog);
        server.setHttpsConfigurator(Objects.requireNonNull(tls, "tls"));
        return new EconomyGatewayServer(server, economy, authenticator);
    }

    public void setExecutor(Executor executor) {
        server.setExecutor(Objects.requireNonNull(executor, "executor"));
    }

    public void start() {
        server.start();
    }

    private void charge(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange);
            return;
        }
        Optional<EconomyGatewayPrincipal> principal = principal(exchange, EconomyGatewayCapability.CHARGE_AND_DISPATCH);
        if (principal.isEmpty()) {
            forbidden(exchange);
            return;
        }
        EconomyGatewayChargeRequest request;
        try {
            request = GSON.fromJson(readBody(exchange), EconomyGatewayChargeRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
        } catch (RuntimeException invalid) {
            sendError(exchange, 400, "Invalid Economy workflow request");
            return;
        }
        EconomyWorkflowRequest workflow = new EconomyWorkflowRequest(
                new EconomyWorkflowRef(principal.get().source(), request.workflowId()), request.account(), request.amount(),
                request.actorPlayerId(), request.actorName(), request.reason(), request.eventType(), request.metadata());
        economy.chargeAndDispatch(workflow).whenComplete((result, failure) -> {
            try {
                if (failure != null) {
                    sendError(exchange, 503, "Economy gateway is temporarily unavailable");
                } else {
                    sendJson(exchange, 200, result);
                }
            } catch (IOException ignored) {
                // The caller disconnected after the Economy result was established; it can retry
                // using the same workflow ID and receive the durable idempotent result.
            }
        });
    }

    private void status(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange);
            return;
        }
        Optional<EconomyGatewayPrincipal> principal = principal(exchange, EconomyGatewayCapability.READ_WORKFLOW);
        if (principal.isEmpty()) {
            forbidden(exchange);
            return;
        }
        String workflowId = query(exchange, "workflowId");
        if (workflowId == null || workflowId.isBlank()) {
            sendError(exchange, 400, "workflowId is required");
            return;
        }
        EconomyWorkflowRef reference;
        try {
            reference = new EconomyWorkflowRef(principal.get().source(), workflowId);
        } catch (IllegalArgumentException invalid) {
            sendError(exchange, 400, "Invalid workflowId");
            return;
        }
        economy.workflow(reference).whenComplete((result, failure) -> {
            try {
                if (failure != null) {
                    sendError(exchange, 503, "Economy gateway is temporarily unavailable");
                } else if (result.isEmpty()) {
                    sendError(exchange, 404, "Workflow was not found");
                } else {
                    sendJson(exchange, 200, result.get());
                }
            } catch (IOException ignored) {
                // Nothing to recover: the result remains queryable by workflow ID.
            }
        });
    }

    private Optional<EconomyGatewayPrincipal> principal(HttpExchange exchange, EconomyGatewayCapability required) {
        if (!(exchange instanceof HttpsExchange https)) {
            return Optional.empty();
        }
        try {
            return authenticator.authenticate(new EconomyGatewayRequestContext(exchange.getRequestHeaders(),
                    https.getSSLSession())).filter(candidate -> candidate.permits(required));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Request body is too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String query(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String candidate = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
            if (key.equals(candidate)) {
                return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void methodNotAllowed(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Allow", "GET, POST");
        sendError(exchange, 405, "Method is not allowed");
    }

    private static void forbidden(HttpExchange exchange) throws IOException {
        sendError(exchange, 403, "Economy gateway access is forbidden");
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("message", message));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
