package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitBaseFeatureTest {

    @Test
    void cleanupRunsLifecycleAfterDisableFailureAndAggregatesErrors() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("base-feature-test"));
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        IllegalStateException disableFailure = new IllegalStateException("disable");
        IllegalArgumentException lifecycleFailure = new IllegalArgumentException("lifecycle");
        doThrow(lifecycleFailure).when(lifecycle).cleanup();
        TestFeature feature = new TestFeature(context(plugin, lifecycle), disableFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, feature::cleanup);

        assertSame(disableFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(lifecycleFailure, thrown.getSuppressed()[0]);
        verify(lifecycle).cleanup();
    }

    private static FeatureContext<TestMeta> context(
            ServerFeatures plugin,
            FeatureLifecycleManager lifecycle
    ) {
        return new FeatureContext<>(
                plugin,
                new TestMeta(),
                mock(FeatureConfigHandler.class),
                lifecycle,
                mock(FeatureLogger.class),
                mock(LocalizationHandler.class)
        );
    }

    private static final class TestFeature extends BukkitBaseFeature<TestMeta> {
        private final RuntimeException disableFailure;

        private TestFeature(FeatureContext<TestMeta> context, RuntimeException disableFailure) {
            super(context);
            this.disableFailure = disableFailure;
        }

        @Override
        public ConfigMap getDefaultConfig() {
            return new ConfigMap();
        }

        @Override
        public MessageMap getDefaultMessages() {
            return new MessageMap();
        }

        @Override
        public void initialize() {
        }

        @Override
        public void disable() {
            throw disableFailure;
        }
    }

    private static final class TestMeta implements BaseMeta {
        @Override
        public String getFeatureName() {
            return "Test";
        }

        @Override
        public String getFeatureVersion() {
            return "1.0";
        }
    }
}
