package nl.hauntedmc.serverfeatures.framework.localization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizationHandlerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void registersDefaultsInTheFeatureDirectory() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        MessageMap defaults = new MessageMap();
        defaults.add("demo.message", "default");

        feature.registerDefaultMessages(defaults);

        ConfigView featureMessages = service.view("features/Demo/messages.yml", false);
        assertEquals("default", featureMessages.get("demo.message", String.class));
    }

    @Test
    void playerMessageContextReusesStaticComponentsAndObservesTemplateChanges() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        LocalizationHandler feature = framework.openFeatureLocalization("Scoreboard");
        ConfigView messages = service.view("features/Scoreboard/messages.yml", false);
        messages.put("scoreboard.line1", "<green>Static line</green>");
        Player player = player();

        Component first = feature.messagesFor(player).build("scoreboard.line1");
        Component second = feature.messagesFor(player).build("scoreboard.line1");

        assertSame(first, second);
        assertEquals("Static line", plain(first));

        messages.put("scoreboard.line1", "<yellow>Changed line</yellow>");
        Component changed = feature.messagesFor(player).build("scoreboard.line1");

        assertNotSame(first, changed);
        assertEquals("Changed line", plain(changed));
    }

    private static Player player() {
        UUID uniqueId = UUID.fromString("f2c17a4c-9caf-4c8e-a68b-9db68706cc80");
        return InterfaceProxy.of(Player.class, Map.of("getUniqueId", arguments -> uniqueId));
    }

    private static String plain(Component component) {
        StringBuilder output = new StringBuilder();
        appendText(component, output);
        return output.toString();
    }

    private static void appendText(Component component, StringBuilder output) {
        if (component instanceof TextComponent text) {
            output.append(text.content());
        }
        component.children().forEach(child -> appendText(child, output));
    }
}
