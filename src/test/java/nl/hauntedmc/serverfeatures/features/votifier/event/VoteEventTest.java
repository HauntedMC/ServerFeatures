package nl.hauntedmc.serverfeatures.features.votifier.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class VoteEventTest {

    @Test
    void exposesPayloadAndSharedHandlerListAcrossInstances() {
        VotePayload payload = new VotePayload("svc", "player", "127.0.0.1", 1L);
        VoteEvent first = new VoteEvent(payload);
        VoteEvent second = new VoteEvent(null);

        assertSame(payload, first.getVote());
        assertNull(second.getVote());
        assertSame(VoteEvent.getHandlerList(), first.getHandlers());
        assertSame(first.getHandlers(), second.getHandlers());
    }
}
