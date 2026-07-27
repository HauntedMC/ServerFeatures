package nl.hauntedmc.serverfeatures.framework.feature;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        FeatureContext<ExampleMeta> first = factory.createContext(new ExampleMeta("Example"));
        FeatureContext<ExampleMeta> second = factory.createContext(new ExampleMeta("Example"));

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

    @Test
    void directAccessorsShareTheSameNormalizedScopeAsCreatedContext() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        List<String> createdNames = new ArrayList<>();
        FeatureScopeFactory factory = new FeatureScopeFactory(
                plugin,
                name -> mock(FeatureLifecycleManager.class),
                name -> {
                    createdNames.add("config:" + name);
                    return config;
                },
                name -> {
                    createdNames.add("localization:" + name);
                    return localization;
                },
                name -> {
                    createdNames.add("logger:" + name);
                    return logger;
                }
        );

        FeatureContext<ExampleMeta> context = factory.createContext(new ExampleMeta(" Fancy Feature "));

        assertSame(context.configHandler(), factory.config("fancy-feature"));
        assertSame(context.localizationHandler(), factory.localization(" FANCY FEATURE "));
        assertSame(context.logger(), factory.logger("fancy_feature"));
        assertEquals(List.of(
                "config:fancy-feature",
                "localization:fancy-feature",
                "logger:fancy-feature"
        ), createdNames);
    }

    @Test
    void differentFeatureNamesReceiveIsolatedStableScopes() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        AtomicInteger configCalls = new AtomicInteger();
        AtomicInteger localizationCalls = new AtomicInteger();
        AtomicInteger loggerCalls = new AtomicInteger();
        FeatureScopeFactory factory = new FeatureScopeFactory(
                plugin,
                name -> mock(FeatureLifecycleManager.class),
                name -> {
                    configCalls.incrementAndGet();
                    return mock(FeatureConfigHandler.class);
                },
                name -> {
                    localizationCalls.incrementAndGet();
                    return mock(LocalizationHandler.class);
                },
                name -> {
                    loggerCalls.incrementAndGet();
                    return mock(FeatureLogger.class);
                }
        );

        FeatureContext<ExampleMeta> first = factory.createContext(new ExampleMeta("First"));
        FeatureContext<ExampleMeta> second = factory.createContext(new ExampleMeta("Second"));

        assertNotSame(first.configHandler(), second.configHandler());
        assertNotSame(first.localizationHandler(), second.localizationHandler());
        assertNotSame(first.logger(), second.logger());
        assertEquals(2, configCalls.get());
        assertEquals(2, localizationCalls.get());
        assertEquals(2, loggerCalls.get());
    }

    @Test
    void clearingCachedScopesForcesAllStableResourcesToBeRecreated() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        AtomicInteger configCalls = new AtomicInteger();
        AtomicInteger localizationCalls = new AtomicInteger();
        AtomicInteger loggerCalls = new AtomicInteger();
        FeatureScopeFactory factory = new FeatureScopeFactory(
                plugin,
                name -> mock(FeatureLifecycleManager.class),
                name -> {
                    configCalls.incrementAndGet();
                    return mock(FeatureConfigHandler.class);
                },
                name -> {
                    localizationCalls.incrementAndGet();
                    return mock(LocalizationHandler.class);
                },
                name -> {
                    loggerCalls.incrementAndGet();
                    return mock(FeatureLogger.class);
                }
        );

        FeatureContext<ExampleMeta> beforeClear = factory.createContext(new ExampleMeta("Example"));
        factory.clearCachedScopes();
        FeatureContext<ExampleMeta> afterClear = factory.createContext(new ExampleMeta("Example"));

        assertNotSame(beforeClear.configHandler(), afterClear.configHandler());
        assertNotSame(beforeClear.localizationHandler(), afterClear.localizationHandler());
        assertNotSame(beforeClear.logger(), afterClear.logger());
        assertEquals(2, configCalls.get());
        assertEquals(2, localizationCalls.get());
        assertEquals(2, loggerCalls.get());
    }

    @Test
    void nullMetadataIsRejectedBeforeFactoriesAreInvoked() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        AtomicInteger calls = new AtomicInteger();
        FeatureScopeFactory factory = new FeatureScopeFactory(
                plugin,
                name -> {
                    calls.incrementAndGet();
                    return mock(FeatureLifecycleManager.class);
                },
                name -> {
                    calls.incrementAndGet();
                    return mock(FeatureConfigHandler.class);
                },
                name -> {
                    calls.incrementAndGet();
                    return mock(LocalizationHandler.class);
                },
                name -> {
                    calls.incrementAndGet();
                    return mock(FeatureLogger.class);
                }
        );

        assertThrows(NullPointerException.class, () -> factory.createContext(null));
        assertEquals(0, calls.get());
    }

    @Test
    void nullResourcesReturnedByFactoriesFailImmediately() {
        ServerFeatures plugin = mock(ServerFeatures.class);
        FeatureScopeFactory factory = new FeatureScopeFactory(
                plugin,
                name -> mock(FeatureLifecycleManager.class),
                name -> null,
                name -> mock(LocalizationHandler.class),
                name -> mock(FeatureLogger.class)
        );

        assertThrows(NullPointerException.class, () -> factory.config("Example"));
    }

    private record ExampleMeta(String featureName) implements BaseMeta {
        @Override
        public String getFeatureName() {
            return featureName;
        }

        @Override
        public String getFeatureVersion() {
            return "1.0";
        }
    }
}
