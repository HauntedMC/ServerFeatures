package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionResolverTest {

    @Test
    void prefersServerMinecraftVersionWhenAvailable() {
        Server server = InterfaceProxy.of(Server.class, Map.of(
                "getMinecraftVersion", args -> " 1.21.1 ",
                "getVersion", args -> "Paper (MC: 1.21.1)"
        ));

        String resolved = VersionResolver.readServerMinecraftVersion(server);

        assertEquals("1.21.1", resolved);
    }

    @Test
    void fallsBackToBukkitBaseVersionWhenServerMinecraftVersionIsBlank() {
        String resolved = VersionResolver.resolveVersionFromStrings(
                "   ",
                "1.20.6-R0.1-SNAPSHOT",
                "Paper something",
                new FeatureLogger(Logger.getAnonymousLogger(), "test")
        );

        assertEquals("1.20.6", resolved);
    }

    @Test
    void fallsBackToMcPatternInServerVersionWhenBukkitVersionIsBlank() {
        String resolved = VersionResolver.resolveVersionFromStrings(
                "",
                "   ",
                "git-Paper-123 (MC: 1.19.4)",
                new FeatureLogger(Logger.getAnonymousLogger(), "test")
        );

        assertEquals("1.19.4", resolved);
    }
}
