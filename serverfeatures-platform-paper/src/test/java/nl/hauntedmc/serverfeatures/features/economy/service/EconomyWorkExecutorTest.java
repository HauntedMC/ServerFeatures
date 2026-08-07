package nl.hauntedmc.serverfeatures.features.economy.service;

import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyWorkExecutorTest {

    @Test
    void rejectsWorkBeyondItsBoundedAdmissionQueue() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        try (EconomyWorkExecutor executor = new EconomyWorkExecutor(
                new EconomySettings.Execution(1, 1, Duration.ofSeconds(1), Duration.ZERO))) {
            var running = executor.submit(() -> {
                started.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return 1;
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            var queued = executor.submit(() -> 2);
            var rejected = executor.submit(() -> 3);

            assertTrue(rejected.isCompletedExceptionally());
            assertEquals(1, executor.queuedTasks());
            unblock.countDown();
            assertEquals(1, running.get(1, TimeUnit.SECONDS));
            assertEquals(2, queued.get(1, TimeUnit.SECONDS));
        }
    }
}
