package nl.hauntedmc.serverfeatures.api.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenResultTest {

    @Test
    void factoriesCreateExpectedStatesAndPayloads() {
        TokenResult<String> ok = TokenResult.ok("payload");
        assertEquals(TokenResult.State.OK, ok.state());
        assertEquals("payload", ok.payload());

        assertEquals(TokenResult.State.EXPIRED, TokenResult.expired().state());
        assertEquals(TokenResult.State.LOADING, TokenResult.loading().state());
        assertEquals(TokenResult.State.EMPTY, TokenResult.empty().state());
        assertEquals(TokenResult.State.INVALID, TokenResult.invalid().state());
        assertNull(TokenResult.invalid().payload());
    }
}
