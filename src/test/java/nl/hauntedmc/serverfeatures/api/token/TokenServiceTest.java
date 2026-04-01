package nl.hauntedmc.serverfeatures.api.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    @Test
    void finiteTokenConsumesUntilRemoved() {
        TokenService<String> service = new TokenService<>("test");
        String token = service.create(
                () -> CompletableFuture.completedFuture("value"),
                TokenOptions.builder().maxUses(2).expireAfter(Duration.ofMinutes(1)).build()
        );

        assertEquals(TokenResult.State.OK, service.consume(token).state());
        assertEquals(TokenResult.State.OK, service.consume(token).state());
        assertEquals(TokenResult.State.INVALID, service.consume(token).state());
    }

    @Test
    void loadingStateTransitionsToOkAfterFutureCompletes() {
        TokenService<String> service = new TokenService<>("test");
        CompletableFuture<String> future = new CompletableFuture<>();
        String token = service.create(() -> future, TokenOptions.infinite());

        assertEquals(TokenResult.State.LOADING, service.consume(token).state());
        future.complete("done");

        assertEquals(TokenResult.State.OK, service.consume(token).state());
        assertEquals("done", service.consume(token).payload());
    }

    @Test
    void emptyPayloadCanConsumeOrNotConsumeBasedOnOption() {
        TokenService<String> service = new TokenService<>("test");

        String consumeEmpty = service.create(
                () -> CompletableFuture.completedFuture(null),
                TokenOptions.builder().maxUses(1).consumeOnEmpty(true).build()
        );
        assertEquals(TokenResult.State.EMPTY, service.consume(consumeEmpty).state());
        assertEquals(TokenResult.State.INVALID, service.consume(consumeEmpty).state());

        String keepEmpty = service.create(
                () -> CompletableFuture.completedFuture(null),
                TokenOptions.builder().maxUses(1).consumeOnEmpty(false).build()
        );
        assertEquals(TokenResult.State.EMPTY, service.consume(keepEmpty).state());
        assertEquals(TokenResult.State.EMPTY, service.consume(keepEmpty).state());
    }

    @Test
    void tokenExpiresByTimeAndIsRemoved() throws Exception {
        TokenService<String> service = new TokenService<>("test");
        String token = service.create(
                () -> CompletableFuture.completedFuture("x"),
                TokenOptions.builder().expireAfter(Duration.ofMillis(5)).build()
        );

        Thread.sleep(15);
        assertEquals(TokenResult.State.EXPIRED, service.consume(token).state());
        assertEquals(TokenResult.State.INVALID, service.consume(token).state());
    }

    @Test
    void handlesLoaderFailuresAndRevocation() {
        TokenService<String> service = new TokenService<>("test");

        String thrown = service.create(() -> {
            throw new RuntimeException("boom");
        }, TokenOptions.infinite());
        assertEquals(TokenResult.State.EMPTY, service.consume(thrown).state());

        String nullFuture = service.create(() -> null, TokenOptions.infinite());
        assertEquals(TokenResult.State.EMPTY, service.consume(nullFuture).state());

        service.revoke(nullFuture);
        assertEquals(TokenResult.State.INVALID, service.consume(nullFuture).state());
        assertTrue(service.toString().contains("size="));
    }
}
