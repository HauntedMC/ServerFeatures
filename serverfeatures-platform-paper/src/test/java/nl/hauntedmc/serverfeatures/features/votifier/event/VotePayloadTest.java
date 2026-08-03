package nl.hauntedmc.serverfeatures.features.votifier.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VotePayloadTest {

    @Test
    void nullAddressFallsBackToDash() {
        VotePayload payload = new VotePayload("svc", "player", null, 123L, "vote.123");

        assertEquals("svc", payload.serviceName());
        assertEquals("player", payload.username());
        assertEquals("-", payload.address());
        assertEquals(123L, payload.voteTimestamp());
        assertEquals("vote.123", payload.processingKey());
    }

    @Test
    void nonNullAddressIsRetained() {
        VotePayload payload = new VotePayload(
                "svc",
                "player",
                "127.0.0.1",
                5L,
                "vote.explicit"
        );

        assertEquals("127.0.0.1", payload.address());
    }

    @Test
    void durableProcessingKeyIsTrimmedAndRetained() {
        VotePayload payload = new VotePayload(
                "svc",
                "player",
                "127.0.0.1",
                5L,
                "  vote.explicit  "
        );

        assertEquals("vote.explicit", payload.processingKey());
    }

    @Test
    void blankProcessingKeyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VotePayload("svc", "player", "127.0.0.1", 5L, " ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VotePayload("svc", "player", "127.0.0.1", 5L, null)
        );
    }
}
