package nl.hauntedmc.serverfeatures.features.vanish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanishTest {

    @Test
    void durableStreamUsesStableFallback() {
        assertEquals("proxy.vanish.update", Vanish.resolveStream(null));
        assertEquals("proxy.vanish.update", Vanish.resolveStream("  "));
        assertEquals("custom.vanish", Vanish.resolveStream(" custom.vanish "));
    }
}
