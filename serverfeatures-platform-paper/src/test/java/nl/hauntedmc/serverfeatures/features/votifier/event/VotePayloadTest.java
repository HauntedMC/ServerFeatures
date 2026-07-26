package nl.hauntedmc.serverfeatures.features.votifier.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VotePayloadTest {

    @Test
    void nullAddressFallsBackToDash() {
        VotePayload payload = new VotePayload("svc", "player", null, 123L);

        assertEquals("svc", payload.serviceName());
        assertEquals("player", payload.username());
        assertEquals("-", payload.address());
        assertEquals("123", payload.getTimeStamp());
        assertEquals(
                VotePayload.stableProcessingKey("svc", "player", null, 123L),
                payload.processingKey()
        );
    }

    @Test
    void nonNullAddressIsRetained() {
        VotePayload payload = new VotePayload("svc", "player", "127.0.0.1", 5L);

        assertEquals("127.0.0.1", payload.address());
    }

    @Test
    void durableProcessingKeyIsRetained() {
        VotePayload payload = new VotePayload("svc", "player", "127.0.0.1", 5L, "vote.explicit");

        assertEquals("vote.explicit", payload.processingKey());
    }
}
