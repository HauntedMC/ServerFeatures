package nl.hauntedmc.serverfeatures.features.votifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VotifierTest {

    @Test
    void derivesPrivateStreamFromVelocityServerName() {
        assertEquals(
                "proxy.votifier.vote.survival-eu",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        "Survival-EU"
                )
        );
    }

    @Test
    void streamIdentityMatchesProxyNormalization() {
        assertEquals("survival_eu-2", Votifier.normalizeTargetServerName("Survival_EU-2"));
        assertEquals(
                "proxy.votifier.vote.survival_eu-2",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        "Survival_EU-2"
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
    void rejectsAmbiguousOrInvalidServerIdentity() {
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
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        "Survival EU"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        "{channel}.{server}",
                        "Survival:EU"
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
                Votifier.resolveConsumerGroup("", "Survival_EU")
        );
        assertEquals(
                "custom_votes",
                Votifier.resolveConsumerGroup(" Custom Votes ", "survival")
        );
    }

    @Test
    void consumerGroupRequiresValidBackendIdentity() {
        assertThrows(
                IllegalStateException.class,
                () -> Votifier.resolveConsumerGroup("", "server")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveConsumerGroup("", "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Votifier.resolveConsumerGroup("custom", "Survival EU")
        );
    }
}
