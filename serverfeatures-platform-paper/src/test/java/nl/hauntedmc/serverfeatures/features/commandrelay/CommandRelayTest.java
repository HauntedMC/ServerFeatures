package nl.hauntedmc.serverfeatures.features.commandrelay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandRelayTest {

    @Test
    void resolvesStableSafeServerAndConsumerNames() {
        assertEquals("server", CommandRelay.resolveServerName("   "));
        assertEquals(
                "serverfeatures.commandrelay.survival_1",
                CommandRelay.resolveConsumerGroup("", "Survival 1")
        );
        assertEquals(
                "custom:relay_group",
                CommandRelay.resolveConsumerGroup(" Custom:Relay Group ", "ignored")
        );
    }
}
