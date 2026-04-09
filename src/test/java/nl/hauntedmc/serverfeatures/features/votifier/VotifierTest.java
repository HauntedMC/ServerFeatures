package nl.hauntedmc.serverfeatures.features.votifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VotifierTest {

    @Test
    void resolveChannelFallsBackToDefaultWhenMissing() {
        assertEquals("proxy.votifier.vote", Votifier.resolveChannel(null));
        assertEquals("proxy.votifier.vote", Votifier.resolveChannel(""));
        assertEquals("proxy.votifier.vote", Votifier.resolveChannel("   "));
    }

    @Test
    void resolveChannelUsesTrimmedConfiguredValue() {
        assertEquals("proxy.custom.vote", Votifier.resolveChannel(" proxy.custom.vote "));
    }
}
