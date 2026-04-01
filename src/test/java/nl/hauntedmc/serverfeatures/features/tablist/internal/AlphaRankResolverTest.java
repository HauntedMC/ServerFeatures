package nl.hauntedmc.serverfeatures.features.tablist.internal;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AlphaRankResolverTest {

    @Test
    void resolverIsDeterministicAndAlwaysReportsNotReady() {
        AlphaRankResolver resolver = new AlphaRankResolver();
        Player player = InterfaceProxy.of(Player.class, Map.of());

        assertEquals("default", resolver.getRank(player));
        assertEquals("default", resolver.getRank(null));
        assertFalse(resolver.isReady());
        assertFalse(resolver.isReady());
    }
}
