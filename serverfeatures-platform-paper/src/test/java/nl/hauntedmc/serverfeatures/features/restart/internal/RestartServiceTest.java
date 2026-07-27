package nl.hauntedmc.serverfeatures.features.restart.internal;

import nl.hauntedmc.serverfeatures.features.restart.Restart;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestartServiceTest {

    @Test
    void usesSafeDefaultScheduleWhenConfigurationIsAbsent() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(null);

        assertEquals(List.of(60, 30, 0), schedule(service));
        assertEquals(5, intField(service, "waitAfterNowSeconds"));
    }

    @Test
    void sortsDeduplicatesAndParsesMixedScheduleValues() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(List.of(30, " 90 ", 30L, "invalid", -5, 10.9, "0"));

        assertEquals(List.of(90, 30, 10, 0), schedule(service));
    }

    @Test
    void appendsFinalZeroAnnouncementWhenOmitted() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(List.of(120, 60, 15));

        assertEquals(List.of(120, 60, 15, 0), schedule(service));
    }

    @Test
    void emptyOrEntirelyInvalidScheduleStillProducesImmediateRestartStep() throws ReflectiveOperationException {
        assertEquals(List.of(0), schedule(serviceWithSchedule(List.of())));
        assertEquals(List.of(0), schedule(serviceWithSchedule(List.of("bad", -1, -20L))));
    }

    @Test
    void preservesConfiguredPostCountdownWait() throws ReflectiveOperationException {
        Restart feature = baseFeature();
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        when(config.get("announce.schedule")).thenReturn(List.of(0));
        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getInt("auto.wait_after_now_seconds", 5)).thenReturn(17);

        RestartService service = new RestartService(feature);

        assertEquals(17, intField(service, "waitAfterNowSeconds"));
    }

    @Test
    void cancellationInvalidatesSequenceAndAllowsAnotherCommandedRestart() throws ReflectiveOperationException {
        RestartService service = serviceWithSchedule(List.of(0));
        long initialToken = longField(service, "sequenceToken");

        service.cancelIfRunning();

        assertEquals(initialToken + 1, longField(service, "sequenceToken"));
        assertEquals(false, booleanField(service, "inProgress"));
    }

    private static RestartService serviceWithSchedule(Object schedule) {
        Restart feature = baseFeature();
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        when(config.get("announce.schedule")).thenReturn(schedule);
        when(feature.getConfigHandler()).thenReturn(config);
        return new RestartService(feature);
    }

    private static Restart baseFeature() {
        Restart feature = mock(Restart.class);
        when(feature.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        return feature;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> schedule(RestartService service) throws ReflectiveOperationException {
        return (List<Integer>) field(service, "scheduleDesc");
    }

    private static int intField(RestartService service, String name) throws ReflectiveOperationException {
        return (int) field(service, name);
    }

    private static long longField(RestartService service, String name) throws ReflectiveOperationException {
        Object atomic = field(service, name);
        return ((java.util.concurrent.atomic.AtomicLong) atomic).get();
    }

    private static boolean booleanField(RestartService service, String name) throws ReflectiveOperationException {
        Object atomic = field(service, name);
        return ((java.util.concurrent.atomic.AtomicBoolean) atomic).get();
    }

    private static Object field(RestartService service, String name) throws ReflectiveOperationException {
        Field field = RestartService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }
}
