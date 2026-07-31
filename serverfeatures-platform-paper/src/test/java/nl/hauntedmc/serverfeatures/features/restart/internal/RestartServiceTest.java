package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartLifecyclePublisher;
import nl.hauntedmc.serverfeatures.features.restart.messaging.RestartMarker;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestartServiceTest {

    @Test
    void usesSafeDefaultsWhenConfigurationIsAbsent() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(null);

        assertEquals(List.of(60, 30, 0), schedule(service));
        assertEquals(5, intField(service, "waitAfterNowSeconds"));
        assertEquals(150L, longFieldValue(service, "drainPlayerIntervalMillis"));
        assertEquals(20_000L, longFieldValue(service, "drainMaxWaitMillis"));
        assertTrue(service.isAcceptingJoins());
        assertEquals(RestartService.Phase.IDLE, service.getPhase());
    }

    @Test
    void sortsDeduplicatesAndParsesMixedScheduleValues() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(
                List.of(30, " 90 ", 30L, "invalid", -5, 10.9, "0")
        );

        assertEquals(List.of(90, 30, 10, 0), schedule(service));
    }

    @Test
    void appendsFinalZeroAnnouncementWhenOmitted() throws ReflectiveOperationException {
        assertEquals(
                List.of(120, 60, 15, 0),
                schedule(serviceWithSchedule(List.of(120, 60, 15)))
        );
    }

    @Test
    void entirelyInvalidScheduleStillProducesImmediateRestartStep()
            throws ReflectiveOperationException {
        assertEquals(List.of(0), schedule(serviceWithSchedule(List.of())));
        assertEquals(
                List.of(0),
                schedule(serviceWithSchedule(List.of("bad", -1, -20L)))
        );
    }

    @Test
    void cancelRestoresIdleStateAndReopensJoins() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(List.of(0));
        setField(service, "phase", RestartService.Phase.PREPARING);
        setField(service, "joinsClosed", true);
        long initialToken = atomicLong(service, "sequenceToken").get();

        RestartService.CancelResult result = service.cancelRestart();

        assertEquals(RestartService.CancelResult.PREPARING, result);
        assertEquals(RestartService.Phase.IDLE, service.getPhase());
        assertTrue(service.isAcceptingJoins());
        assertEquals(initialToken + 1L, atomicLong(service, "sequenceToken").get());
    }

    @Test
    void cancellingPreparedRestartPublishesCancelForExactMarker()
            throws ReflectiveOperationException {
        Restart feature = featureWithSchedule(List.of(0));
        RestartLifecyclePublisher publisher = mock(RestartLifecyclePublisher.class);
        RestartMarker marker = new RestartMarker(
                "restart-one",
                "survival",
                1L,
                2L,
                3L,
                4L
        );
        when(publisher.publishCancel(marker)).thenReturn(CompletableFuture.completedFuture(null));
        RestartService service = new RestartService(feature, publisher);
        setField(service, "phase", RestartService.Phase.PREPARING);
        setField(service, "joinsClosed", true);
        setField(service, "preparedMarker", marker);
        atomicBoolean(service, "shutdownCommitted").set(true);

        assertEquals(RestartService.CancelResult.PREPARING, service.cancelRestart());

        verify(publisher).publishCancel(marker);
        assertFalse(atomicBoolean(service, "shutdownCommitted").get());
        assertEquals(null, field(service, "preparedMarker"));
    }

    @Test
    void cancellationIsRejectedAfterPlayerDrainStarts() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(List.of(0));
        setField(service, "phase", RestartService.Phase.DRAINING);
        setField(service, "joinsClosed", true);
        long token = atomicLong(service, "sequenceToken").get();

        assertEquals(RestartService.CancelResult.TOO_LATE, service.cancelRestart());
        assertEquals(RestartService.Phase.DRAINING, service.getPhase());
        assertFalse(service.isAcceptingJoins());
        assertEquals(token, atomicLong(service, "sequenceToken").get());
    }

    @Test
    void featureDisableDuringActualShutdownPreservesReadyMarker()
            throws ReflectiveOperationException {
        Restart feature = featureWithSchedule(List.of(0));
        RestartLifecyclePublisher publisher = mock(RestartLifecyclePublisher.class);
        RestartMarker marker = new RestartMarker(
                "restart-shutdown",
                "survival",
                1L,
                2L,
                3L,
                4L
        );
        RestartService service = new RestartService(feature, publisher);
        setField(service, "phase", RestartService.Phase.SHUTTING_DOWN);
        setField(service, "preparedMarker", marker);
        atomicBoolean(service, "shutdownCommitted").set(true);
        atomicBoolean(service, "shutdownStarted").set(true);
        long initialToken = atomicLong(service, "sequenceToken").get();

        service.shutdown();

        verify(publisher, never()).publishCancel(marker);
        assertEquals(initialToken, atomicLong(service, "sequenceToken").get());
        assertTrue(atomicBoolean(service, "shutdownCommitted").get());
        assertEquals(RestartService.Phase.SHUTTING_DOWN, service.getPhase());
    }

    private static RestartService serviceWithSchedule(Object schedule) {
        return new RestartService(featureWithSchedule(schedule));
    }

    private static Restart featureWithSchedule(Object schedule) {
        Restart feature = mock(Restart.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        when(config.get("announce.schedule")).thenReturn(schedule);
        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(feature.getLong(anyString(), anyLong())).thenAnswer(invocation -> invocation.getArgument(1));
        when(feature.getPositiveInt(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(feature.getPositiveLong(anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(feature.getBoolean(anyString(), anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(feature.getString(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return feature;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> schedule(RestartService service) throws ReflectiveOperationException {
        return (List<Integer>) field(service, "scheduleDesc");
    }

    private static int intField(RestartService service, String name)
            throws ReflectiveOperationException {
        return (int) field(service, name);
    }

    private static long longFieldValue(RestartService service, String name)
            throws ReflectiveOperationException {
        return (long) field(service, name);
    }

    private static AtomicLong atomicLong(RestartService service, String name)
            throws ReflectiveOperationException {
        return (AtomicLong) field(service, name);
    }

    private static AtomicBoolean atomicBoolean(RestartService service, String name)
            throws ReflectiveOperationException {
        return (AtomicBoolean) field(service, name);
    }

    private static void setField(RestartService service, String name, Object value)
            throws ReflectiveOperationException {
        Field field = RestartService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static Object field(RestartService service, String name)
            throws ReflectiveOperationException {
        Field field = RestartService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }
}
