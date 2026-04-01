package nl.hauntedmc.serverfeatures.api.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenOptionsTest {

    @Test
    void builderAppliesValuesAndNormalizesExpiration() {
        TokenOptions opts = TokenOptions.builder()
                .maxUses(5)
                .expireAfter(Duration.ZERO)
                .consumeOnEmpty(false)
                .build();

        assertEquals(5, opts.maxUses());
        assertEquals(1L, opts.expireMillis());
        assertTrue(!opts.consumeOnEmpty());
    }

    @Test
    void nullOrNegativeDurationMeansNeverExpires() {
        assertEquals(Long.MAX_VALUE, TokenOptions.builder().expireAfter(null).build().expireMillis());
        assertEquals(Long.MAX_VALUE, TokenOptions.builder().expireAfter(Duration.ofMillis(-1)).build().expireMillis());
    }

    @Test
    void convenienceFactoriesWork() {
        TokenOptions infinite = TokenOptions.infinite();
        assertEquals(-1, infinite.maxUses());
        assertEquals(Long.MAX_VALUE, infinite.expireMillis());
        assertTrue(infinite.consumeOnEmpty());

        TokenOptions fromOf = TokenOptions.of(2, Duration.ofSeconds(1), false);
        assertEquals(2, fromOf.maxUses());
        assertEquals(1000L, fromOf.expireMillis());
        assertTrue(!fromOf.consumeOnEmpty());
    }
}
