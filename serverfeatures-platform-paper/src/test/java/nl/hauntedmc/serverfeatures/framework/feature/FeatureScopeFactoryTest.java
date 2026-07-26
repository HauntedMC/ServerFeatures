package nl.hauntedmc.serverfeatures.framework.feature;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class FeatureScopeFactoryTest {

    @Test
    void reusesStableScopesButCreatesAFreshLifecycleForEveryLoad() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FeatureLifecycleManager firstLifecycle = mock(FeatureLifecycleManager.class);
        FeatureLifecycleManager secondLifecycle = mock(FeatureLifecycleManager.class);
        AtomicInteger lifecycleCalls = new AtomicInteger();
        AtomicInteger configCalls = new AtomicInteger();
        FeatureScopeFactory factory = new FeatureScopeFactory(
                plugin,
                name -> lifecycleCalls.getAndIncrement() == 0 ? firstLifecycle : secondLifecycle,
                name -> {
                    configCalls.incrementAndGet();
                    return config;
                },
                name -> localization,
                name -> logger
        );

        FeatureContext<ExampleMeta> first = factory.createContext(new ExampleMeta());
        FeatureContext<ExampleMeta> second = factory.createContext(new ExampleMeta());

        assertSame(config, first.configHandler());
        assertSame(config, second.configHandler());
        assertSame(localization, first.localizationHandler());
        assertSame(logger, second.logger());
        assertSame(firstLifecycle, first.lifecycleManager());
        assertSame(secondLifecycle, second.lifecycleManager());
        assertNotSame(first.lifecycleManager(), second.lifecycleManager());
        assertEquals(1, configCalls.get());
        assertEquals(2, lifecycleCalls.get());
    }

    private static final class ExampleMeta implements BaseMeta {
        @Override
        public String getFeatureName() {
            return "Example";
        }

        @Override
        public String getFeatureVersion() {
            return "1.0";
        }
    }
}
