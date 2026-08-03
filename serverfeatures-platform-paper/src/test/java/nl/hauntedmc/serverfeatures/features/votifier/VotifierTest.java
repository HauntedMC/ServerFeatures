package nl.hauntedmc.serverfeatures.features.votifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VotifierTest {

    @Test
    void derivesPrivateStreamFromServerName() {
        assertEquals(
                "proxy.votifier.vote.survival_eu",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        " Survival EU "
                )
        );
    }

    @Test
    void streamNormalizationMatchesProxyTargets() {
        assertEquals("survival_eu-2", Votifier.normalizeTargetServerName(" Survival:EU-2 "));
        assertEquals(
                "proxy.votifier.vote.survival_eu-2",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        " Survival:EU-2 "
                )
        );
    }

    @Test
    void supportsCustomPrivateStreamPattern() {
        assertEquals(
                "votes:skyblock:proxy.votifier.vote",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "votes:{server}:{channel}",
                        "skyblock"
                )
        );
    }

    @Test
    void rejectsAmbiguousServerIdentity() {
        assertThrows(
                IllegalStateException.class,
                () -> Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        "server"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        ""
                )
        );
    }

    @Test
    void rejectsInvalidStreamConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveDeliveryStream("", "{channel}.{server}", "survival")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveDeliveryStream("proxy.votifier.vote", "", "survival")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveDeliveryStream("proxy.votifier.vote", "one-stream", "survival")
        );
    }

    @Test
    void consumerGroupDefaultsToStableBackendIdentity() {
        assertEquals(
                "serverfeatures.votifier.survival_eu",
                Votifier.resolveConsumerGroup("", " Survival EU ")
        );
        assertEquals(
                "custom_votes",
                Votifier.resolveConsumerGroup(" Custom Votes ", "survival")
        );
    }

    @Test
    void consumerGroupAlsoRejectsAmbiguousServerIdentity() {
        assertThrows(
                IllegalStateException.class,
                () -> Votifier.resolveConsumerGroup("", "server")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveConsumerGroup("", "")
        );
    }
}
