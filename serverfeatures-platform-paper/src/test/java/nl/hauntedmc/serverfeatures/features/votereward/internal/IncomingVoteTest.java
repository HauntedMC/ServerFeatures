package nl.hauntedmc.serverfeatures.features.votereward.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncomingVoteTest {

    @Test
    void durableProcessingKeyIsRequiredAndNormalized() {
        IncomingVote vote = new IncomingVote(
                "svc",
                "player",
                "127.0.0.1",
                123L,
                "  vote.123  "
        );

        assertEquals("vote.123", vote.processingKey());
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncomingVote("svc", "player", "127.0.0.1", 123L, " ")
        );
    }
}
