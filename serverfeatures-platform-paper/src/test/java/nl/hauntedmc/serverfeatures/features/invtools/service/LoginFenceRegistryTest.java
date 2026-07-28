package nl.hauntedmc.serverfeatures.features.invtools.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFenceRegistryTest {

    @Test
    void preventsOfflineOpeningOnlyUntilLoginConfigurationCompletesOrTimesOut() {
        AtomicLong clock = new AtomicLong(1_000L);
        LoginFenceRegistry fences = new LoginFenceRegistry(Duration.ofSeconds(30), clock::get);
        UUID playerId = UUID.randomUUID();

        assertFalse(fences.isFenced(playerId));

        fences.mark(playerId);
        assertTrue(fences.isFenced(playerId));

        fences.clear(playerId);
        assertFalse(fences.isFenced(playerId));

        fences.mark(playerId);
        clock.addAndGet(Duration.ofSeconds(30).toMillis());
        assertFalse(fences.isFenced(playerId));
    }
}
