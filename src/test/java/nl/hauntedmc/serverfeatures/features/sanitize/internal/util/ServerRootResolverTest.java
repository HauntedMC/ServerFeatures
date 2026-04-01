package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerRootResolverTest {

    @Test
    void derivesServerRootFromPluginsDirectoryParent() {
        Path serverRoot = Path.of("/srv/test-root");
        File dataFolder = serverRoot.resolve("plugins").resolve("MyPlugin").toFile();
        Plugin plugin = plugin(dataFolder, serverRoot.resolve("world").toFile());

        File resolved = ServerRootResolver.resolve(plugin, logger());

        assertEquals(serverRoot.toAbsolutePath().toString(), resolved.toPath().toAbsolutePath().toString());
    }

    @Test
    void fallsBackToCurrentDirectoryWhenResolutionThrows() {
        Plugin plugin = InterfaceProxy.of(Plugin.class, Map.of(
                "getDataFolder", args -> {
                    throw new RuntimeException("boom");
                }
        ));

        File resolved = ServerRootResolver.resolve(plugin, logger());

        assertEquals(new File(".").getAbsoluteFile().toPath(), resolved.toPath());
    }

    private static Plugin plugin(File dataFolder, File worldContainer) {
        Server server = InterfaceProxy.of(Server.class, Map.of("getWorldContainer", args -> worldContainer));
        return InterfaceProxy.of(Plugin.class, Map.of(
                "getDataFolder", args -> dataFolder,
                "getServer", args -> server
        ));
    }

    private static FeatureLogger logger() {
        return new FeatureLogger(Logger.getAnonymousLogger(), "test");
    }
}

