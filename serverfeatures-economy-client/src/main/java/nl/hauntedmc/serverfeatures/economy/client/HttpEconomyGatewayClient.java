package nl.hauntedmc.serverfeatures.economy.client;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.api.economy.EconomyGatewayChargeRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** TLS-only HTTP implementation of the official Economy gateway client. */
public final class HttpEconomyGatewayClient implements EconomyGatewayClient {
    private static final Gson GSON = new Gson();

    private final HttpClient http;
    private final URI endpoint;
    private final Supplier<String> bearerToken;

    public HttpEconomyGatewayClient(HttpClient http, URI endpoint, Supplier<String> bearerToken) {
        this.http = Objects.requireNonNull(http, "http");
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.bearerToken = Objects.requireNonNull(bearerToken, "bearerToken");
    }

    @Override
    public CompletionStage<EconomyWorkflowResult> chargeAndDispatch(EconomyGatewayChargeRequest request) {
        Objects.requireNonNull(request, "request");
        HttpRequest httpRequest = request("/v1/economy/workflows/charge")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request), StandardCharsets.UTF_8)).build();
        return http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> result(response, false).thenApply(Optional::orElseThrow));
    }

    @Override
    public CompletionStage<Optional<EconomyWorkflowResult>> workflow(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("workflowId must not be blank"));
        }
        String encoded = URLEncoder.encode(workflowId.trim(), StandardCharsets.UTF_8);
        HttpRequest httpRequest = request("/v1/economy/workflows/status?workflowId=" + encoded).GET().build();
        return http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> result(response, true));
    }

    private HttpRequest.Builder request(String path) {
        String token = bearerToken.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Economy gateway bearer token is unavailable");
        }
        return HttpRequest.newBuilder(endpoint.resolve(path))
                .header("Authorization", "Bearer " + token.trim())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    private static CompletionStage<Optional<EconomyWorkflowResult>> result(HttpResponse<String> response,
                                                                             boolean absentIsEmpty) {
        int status = response.statusCode();
        if (status == 404 && absentIsEmpty) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (status < 200 || status >= 300) {
            return CompletableFuture.failedFuture(new EconomyGatewayException(status, errorMessage(response.body())));
        }
        try {
            return CompletableFuture.completedFuture(Optional.of(GSON.fromJson(response.body(), EconomyWorkflowResult.class)));
        } catch (RuntimeException malformed) {
            return CompletableFuture.failedFuture(new EconomyGatewayException(status,
                    "Economy gateway returned an invalid response: " + malformed.getMessage()));
        }
    }

    private static URI requireHttpsEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Economy gateway endpoint must be an absolute https URI");
        }
        String value = endpoint.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static String errorMessage(String body) {
        try {
            GatewayError error = GSON.fromJson(body, GatewayError.class);
            if (error != null && error.message != null && !error.message.isBlank()) {
                return error.message;
            }
        } catch (RuntimeException ignored) {
            // Preserve a generic error rather than reflecting arbitrary gateway HTML to callers.
        }
        return "Economy gateway request failed";
    }

    private static final class GatewayError {
        private String message;
    }
}
