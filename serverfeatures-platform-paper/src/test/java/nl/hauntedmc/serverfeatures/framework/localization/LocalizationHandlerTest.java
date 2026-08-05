package nl.hauntedmc.serverfeatures.framework.localization;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizationHandlerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void migratesOwnedMessageRootsAndRegistersDefaultsInTheFeatureDirectory() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        ConfigView legacyDefaults = service.view("lang/messages.yml", false);
        ConfigView legacyEnglish = service.view("lang/messages_EN.yml", false);
        legacyDefaults.put("demo.custom", "legacy");
        legacyEnglish.put("demo.custom", "translated");
        MessageMap defaults = new MessageMap();
        defaults.add("demo.custom", "default");
        defaults.add("demo.added", "added");

        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        feature.migrateLegacyFeatureMessages(defaults);
        feature.registerDefaultMessages(defaults);

        ConfigView featureDefaults = service.view("features/Demo/messages.yml", false);
        ConfigView featureEnglish = service.view("features/Demo/messages_EN.yml", false);
        assertEquals("legacy", featureDefaults.get("demo.custom", String.class));
        assertEquals("added", featureDefaults.get("demo.added", String.class));
        assertEquals("translated", featureEnglish.get("demo.custom", String.class));
        assertTrue(legacyDefaults.node("demo").isNull());
        assertTrue(legacyEnglish.node("demo").isNull());
    }

    @Test
    void migrationDoesNotReshapeExistingFeatureMessageBranches() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        ConfigView legacy = service.view("lang/messages.yml", false);
        ConfigView target = service.view("features/Demo/messages.yml", false);
        legacy.put("demo.legacy", "old");
        legacy.put("demoAdded.value", "copied");
        target.put("demo", "current");
        MessageMap defaults = new MessageMap();
        defaults.add("demo.message", "default");
        defaults.add("demoAdded.message", "default");

        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        feature.migrateLegacyFeatureMessages(defaults);

        assertEquals("current", target.get("demo", String.class));
        assertNull(target.get("demo.legacy"));
        assertEquals("copied", target.get("demoAdded.value", String.class));
        assertTrue(legacy.node("demo").isNull());
        assertTrue(legacy.node("demoAdded").isNull());
    }

    @Test
    void messageKeyMigrationPreservesCustomizedLegacyValueOverGeneratedDestination() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        ConfigView target = service.view("features/Demo/messages.yml", false);
        target.put("demo.old", "custom legacy value");
        target.put("demo.new", "new generated default");

        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        feature.migrateMessageKey(
                "demo.old",
                "demo.new",
                "old generated default",
                "new generated default"
        );

        assertNull(target.get("demo.old"));
        assertEquals("custom legacy value", target.get("demo.new", String.class));
    }

    @Test
    void messageKeyMigrationInstallsNewDefaultWhenDestinationIsMissing() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        ConfigView target = service.view("features/Demo/messages.yml", false);
        target.put("demo.old", "old generated default");

        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        feature.migrateMessageKey(
                "demo.old",
                "demo.new",
                "old generated default",
                "new generated default"
        );

        assertNull(target.get("demo.old"));
        assertEquals("new generated default", target.get("demo.new", String.class));
    }

    @Test
    void messageKeyMigrationDoesNotOverwriteCustomizedDestination() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        ConfigView target = service.view("features/Demo/messages.yml", false);
        target.put("demo.old", "custom legacy value");
        target.put("demo.new", "custom destination value");

        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        feature.migrateMessageKey(
                "demo.old",
                "demo.new",
                "old generated default",
                "new generated default"
        );

        assertNull(target.get("demo.old"));
        assertEquals("custom destination value", target.get("demo.new", String.class));
    }

    @Test
    void messageKeyMigrationPreservesLanguageSpecificLegacyValue() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("localization-test"));
        ConfigService service = new ConfigService(plugin);
        LocalizationHandler framework = new LocalizationHandler(plugin, service);
        ConfigView language = service.view("features/Demo/messages_EN.yml", false);
        language.put("demo.old", "translated legacy value");

        LocalizationHandler feature = framework.openFeatureLocalization("Demo");
        feature.migrateMessageKey(
                "demo.old",
                "demo.new",
                "old generated default",
                "new generated default"
        );

        assertNull(language.get("demo.old"));
        assertEquals("translated legacy value", language.get("demo.new", String.class));
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
        return ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
    }
}
