package nl.hauntedmc.serverfeatures.features.votifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void legacyModeConsumesConfiguredChannelExactly() {
        assertEquals(
                "proxy.votifier.vote",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        Votifier.DeliveryMode.LEGACY,
                        "{channel}.{server}",
                        "survival"
                )
        );
    }

    @Test
    void targetedModeDerivesStreamFromServerName() {
        assertEquals(
                "proxy.votifier.vote.survival_eu",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        Votifier.DeliveryMode.TARGETED,
                        "{channel}.{server}",
                        " Survival EU "
                )
        );
    }

    @Test
    void targetedModeSupportsCustomPattern() {
        assertEquals(
                "votes:skyblock:proxy.votifier.vote",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        Votifier.DeliveryMode.TARGETED,
                        "votes:{server}:{channel}",
                        "skyblock"
                )
        );
    }

    @Test
    void targetedModeRejectsDefaultServerIdentity() {
        assertThrows(
                IllegalStateException.class,
                () -> Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        Votifier.DeliveryMode.TARGETED,
                        "{channel}.{server}",
                        "server"
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        Votifier.DeliveryMode.TARGETED,
                        "{channel}.{server}",
                        ""
                )
        );
    }

    @Test
    void invalidTargetPatternFallsBackToDefaultPattern() {
        assertEquals(
                "proxy.votifier.vote.kitpvp",
                Votifier.resolveDeliveryStream(
                        "proxy.votifier.vote",
                        Votifier.DeliveryMode.TARGETED,
                        "one-stream",
                        "kitpvp"
                )
        );
    }

    @Test
    void deliveryModeParsingIsBackwardCompatible() {
        assertEquals(Votifier.DeliveryMode.LEGACY, Votifier.parseDeliveryMode(null));
        assertEquals(Votifier.DeliveryMode.LEGACY, Votifier.parseDeliveryMode("unknown"));
        assertEquals(Votifier.DeliveryMode.TARGETED, Votifier.parseDeliveryMode("targeted"));
        assertEquals(Votifier.DeliveryMode.TARGETED, Votifier.parseDeliveryMode("per_server"));
    }
}
