package nl.hauntedmc.serverfeatures.features.votereward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteRewardTest {

    @Test
    void resolveVoteSourceDefaultsToNativeWhenMissing() {
        VoteReward.ResolvedVoteSource fromNull = VoteReward.resolveVoteSource(null);
        VoteReward.ResolvedVoteSource fromBlank = VoteReward.resolveVoteSource("   ");

        assertEquals(VoteReward.VoteSource.NATIVE, fromNull.source());
        assertFalse(fromNull.invalidConfiguredValue());
        assertEquals(VoteReward.VoteSource.NATIVE, fromBlank.source());
        assertFalse(fromBlank.invalidConfiguredValue());
    }

    @Test
    void resolveVoteSourceSupportsConfiguredValuesCaseInsensitively() {
        VoteReward.ResolvedVoteSource resolved = VoteReward.resolveVoteSource(" VoTiFiEr ");

        assertEquals(VoteReward.VoteSource.VOTIFIER, resolved.source());
        assertFalse(resolved.invalidConfiguredValue());
        assertEquals("votifier", resolved.configuredValue());
    }

    @Test
    void resolveVoteSourceFallsBackToNativeForInvalidValues() {
        VoteReward.ResolvedVoteSource resolved = VoteReward.resolveVoteSource("redis");

        assertEquals(VoteReward.VoteSource.NATIVE, resolved.source());
        assertTrue(resolved.invalidConfiguredValue());
        assertEquals("redis", resolved.configuredValue());
    }

    @Test
    void unavailableSourceWarningReturnsNullWhenSelectedSourceIsAvailable() {
        assertNull(VoteReward.unavailableSourceWarning(VoteReward.VoteSource.NATIVE, true, false));
        assertNull(VoteReward.unavailableSourceWarning(VoteReward.VoteSource.VOTIFIER, false, true));
    }

    @Test
    void unavailableSourceWarningExplainsMissingNativeSourceProducer() {
        String warning = VoteReward.unavailableSourceWarning(VoteReward.VoteSource.NATIVE, false, true);

        assertTrue(warning.contains("ServerFeatures Votifier feature is not enabled"));
        assertTrue(warning.contains("\"native\""));
    }

    @Test
    void unavailableSourceWarningExplainsMissingVotifierPlugin() {
        String warning = VoteReward.unavailableSourceWarning(VoteReward.VoteSource.VOTIFIER, true, false);

        assertTrue(warning.contains("Votifier plugin is not enabled"));
        assertTrue(warning.contains("\"votifier\""));
    }
}
