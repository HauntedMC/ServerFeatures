package nl.hauntedmc.serverfeatures.framework.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeatureLifecycleManagerTest {

    @Test
    void cleanupAttemptsEveryResourceAndAggregatesFailures() {
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        FeatureCommandManager commands = mock(FeatureCommandManager.class);
        FeatureListenerManager listeners = mock(FeatureListenerManager.class);
        FeatureDataManager data = mock(FeatureDataManager.class);
        FeatureCacheManager caches = mock(FeatureCacheManager.class);
        FeatureGUIManager gui = mock(FeatureGUIManager.class);
        FeatureApiManager apis = mock(FeatureApiManager.class);
        IllegalStateException first = new IllegalStateException("gui");
        IllegalArgumentException second = new IllegalArgumentException("listeners");
        doThrow(first).when(gui).shutdown();
        doThrow(second).when(listeners).unregisterAllListeners();
        FeatureLifecycleManager lifecycle = new FeatureLifecycleManager(
                tasks, commands, listeners, data, caches, gui, apis
        );

        RuntimeException thrown = assertThrows(RuntimeException.class, lifecycle::cleanup);

        assertSame(first, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(second, thrown.getSuppressed()[0]);
        verify(tasks).cancelAllTasks();
        verify(commands).unregisterAllFeatureCommands();
        verify(commands).unregisterAllBrigadierCommands();
        verify(apis).unregisterAllServices();
        verify(data).closeAllConnections();
        verify(caches).cleanupAll();
    }
}
