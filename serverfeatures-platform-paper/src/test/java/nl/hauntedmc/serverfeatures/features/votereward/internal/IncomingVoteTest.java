package nl.hauntedmc.serverfeatures.features.votereward.internal;

import nl.hauntedmc.serverfeatures.features.votifier.event.VotePayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomingVoteTest {

    @Test
    void legacyConstructionDerivesTheProducerCompatibleProcessingKey() {
        IncomingVote vote = new IncomingVote("svc", "player", "127.0.0.1", 123L);

        assertEquals(
                VotePayload.stableProcessingKey("svc", "player", "127.0.0.1", 123L),
                vote.processingKey()
        );
    }
}
