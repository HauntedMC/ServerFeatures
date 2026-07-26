package nl.hauntedmc.serverfeatures.framework.localization;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
