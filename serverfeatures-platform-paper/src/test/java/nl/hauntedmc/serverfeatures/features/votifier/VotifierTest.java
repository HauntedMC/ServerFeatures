package nl.hauntedmc.serverfeatures.features.votifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VotifierTest {

    @Test
    void resolveStreamFallsBackToDefaultWhenMissing() {
        assertEquals("proxy.votifier.vote", Votifier.resolveStream(null));
        assertEquals("proxy.votifier.vote", Votifier.resolveStream(""));
        assertEquals("proxy.votifier.vote", Votifier.resolveStream("   "));
    }

    @Test
    void resolveStreamUsesTrimmedConfiguredValue() {
        assertEquals("proxy.custom.vote", Votifier.resolveStream(" proxy.custom.vote "));
    }

    @Test
    void consumerGroupDefaultsToAStablePerServerName() {
        assertEquals(
                "serverfeatures.votifier.survival_eu",
                Votifier.resolveConsumerGroup("", " Survival EU ")
        );
        assertEquals(
                "custom_votes",
                Votifier.resolveConsumerGroup(" Custom Votes ", "ignored")
        );
    }
}
