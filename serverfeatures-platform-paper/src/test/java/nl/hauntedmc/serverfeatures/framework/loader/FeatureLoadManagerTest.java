package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.features.FeatureFactory;
import nl.hauntedmc.serverfeatures.framework.command.sync.CommandSync;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.feature.FeatureScopeFactory;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.loader.disable.FeatureDisableResult;
import nl.hauntedmc.serverfeatures.framework.loader.reload.FeatureReloadResult;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureLoadManagerTest {

    private ServerFeatures plugin;
    private MainConfigHandler mainConfig;
    private FeatureScopeFactory scopeFactory;
    private FeatureLoadManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(ServerFeatures.class);
        mainConfig = mock(MainConfigHandler.class);
        scopeFactory = mock(FeatureScopeFactory.class);
        when(plugin.getConfigHandler()).thenReturn(mainConfig);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("feature-load-manager-test"));
        when(mainConfig.isFeatureEnabled(anyString())).thenReturn(true);
        manager = new FeatureLoadManager(plugin, scopeFactory, false);
    }

    @Test
    void loadCleansFrameworkScopeWhenStorageSetupFailsBeforeInitialization() {
        register(descriptor("Demo", Set.of()));
        FeatureContext<?> context = mock(FeatureContext.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        BukkitBaseFeature<?> feature = feature(config, mock(LocalizationHandler.class));
        when(context.lifecycleManager()).thenReturn(lifecycle);
        doReturn(context).when(scopeFactory).createContext(any());
        doThrow(new IllegalStateException("disk unavailable")).when(config).injectDefaults(any());

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(anyString(), any())).thenReturn(feature);

            assertFalse(manager.loadFeature("Demo"));
        }

        verify(lifecycle).cleanup();
        verify(feature, never()).cleanup();
        assertFalse(manager.getFeatureRegistry().isFeatureLoaded("Demo"));
    }

    @Test
    void loadRunsFeatureCleanupWhenInitializationFails() {
        register(descriptor("Demo", Set.of()));
        FeatureContext<?> context = mock(FeatureContext.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        when(context.lifecycleManager()).thenReturn(lifecycle);
        doReturn(context).when(scopeFactory).createContext(any());
        BukkitBaseFeature<?> feature = feature(mock(FeatureConfigHandler.class), mock(LocalizationHandler.class));
        doThrow(new IllegalStateException("initialize failed")).when(feature).initialize();

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(anyString(), any())).thenReturn(feature);

            assertFalse(manager.loadFeature("Demo"));
        }

        verify(feature).cleanup();
        assertFalse(manager.getFeatureRegistry().isFeatureLoaded("Demo"));
    }

    @Test
    void reloadSnapshotsCascadeAndUnloadsInReverseDependencyOrder() {
        register(descriptor("Root", Set.of()));
        register(descriptor("Child", Set.of("Root")));
        register(descriptor("Grandchild", Set.of("Child")));
        BukkitBaseFeature<?> root = feature();
        BukkitBaseFeature<?> child = feature();
        BukkitBaseFeature<?> grandchild = feature();
        manager.getFeatureRegistry().registerLoadedFeature("Root", root);
        manager.getFeatureRegistry().registerLoadedFeature("Child", child);
        manager.getFeatureRegistry().registerLoadedFeature("Grandchild", grandchild);
        FeatureLoadManager reloading = spy(manager);
        doReturn(true).when(reloading).loadFeature(anyString());

        try (MockedStatic<CommandSync> commandSync = mockStatic(CommandSync.class)) {
            var response = reloading.reloadFeature("Root");

            assertEquals(FeatureReloadResult.SUCCESS, response.result());
            assertEquals(Set.of("Child", "Grandchild"), response.reloadedDependents());
            commandSync.verify(() -> CommandSync.apply(plugin));
        }

        InOrder cleanupOrder = inOrder(grandchild, child, root);
        cleanupOrder.verify(grandchild).cleanup();
        cleanupOrder.verify(child).cleanup();
        cleanupOrder.verify(root).cleanup();
        InOrder loadOrder = inOrder(reloading);
        loadOrder.verify(reloading).loadFeature("Root");
        loadOrder.verify(reloading).loadFeature("Child");
        loadOrder.verify(reloading).loadFeature("Grandchild");
    }

    @Test
    void failedDependencyReplacementLeavesItsDependentsUnloaded() {
        register(descriptor("Root", Set.of()));
        register(descriptor("Child", Set.of("Root")));
        manager.getFeatureRegistry().registerLoadedFeature("Root", feature());
        manager.getFeatureRegistry().registerLoadedFeature("Child", feature());
        FeatureLoadManager reloading = spy(manager);
        doReturn(false).when(reloading).loadFeature("Root");

        try (MockedStatic<CommandSync> commandSync = mockStatic(CommandSync.class)) {
            var response = reloading.reloadFeature("Root");

            assertEquals(FeatureReloadResult.FAILED, response.result());
            commandSync.verify(() -> CommandSync.apply(plugin));
        }

        assertTrue(manager.getFeatureRegistry().getLoadedFeatureNames().isEmpty());
        verify(reloading, never()).loadFeature("Child");
    }

    @Test
    void disableRemovesStaleRegistryEntryEvenWhenCleanupFails() {
        register(descriptor("Demo", Set.of()));
        BukkitBaseFeature<?> feature = feature();
        doThrow(new IllegalStateException("cleanup failed")).when(feature).cleanup();
        manager.getFeatureRegistry().registerLoadedFeature("Demo", feature);

        try (MockedStatic<CommandSync> commandSync = mockStatic(CommandSync.class)) {
            var response = manager.disableFeature("Demo");

            assertEquals(FeatureDisableResult.FAILED, response.result());
            commandSync.verify(() -> CommandSync.apply(plugin));
        }

        assertFalse(manager.getFeatureRegistry().isFeatureLoaded("Demo"));
        verify(mainConfig).setFeatureEnabled("Demo", false);
    }

    private void register(FeatureDescriptor descriptor) {
        manager.getFeatureRegistry().registerAvailableFeature(descriptor);
    }

    private static FeatureDescriptor descriptor(String name, Set<String> dependencies) {
        return new FeatureDescriptor(name, "example." + name, name, "1", dependencies, Set.of());
    }

    private static BukkitBaseFeature<?> feature() {
        return feature(mock(FeatureConfigHandler.class), mock(LocalizationHandler.class));
    }

    private static BukkitBaseFeature<?> feature(
            FeatureConfigHandler config,
            LocalizationHandler localization
    ) {
        BukkitBaseFeature<?> feature = mock(BukkitBaseFeature.class);
        when(feature.getDefaultConfig()).thenReturn(new ConfigMap());
        when(feature.getDefaultMessages()).thenReturn(new MessageMap());
        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        return feature;
    }
}
