package nl.hauntedmc.serverfeatures.features.sanctions.state;

import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.serverfeatures.features.sanctions.service.SanctionsDataService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuteRegistryTest {

    @Test
    void trackRefreshAndRemoveFollowServiceState() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();
        long playerId = 11L;

        service.set(playerId, Optional.of(sanction(1L, "reason", Instant.now(), Instant.now().plusSeconds(120))));
        registry.trackIfMuted(uuid, playerId);
        assertTrue(registry.isMuted(uuid));
        assertTrue(registry.get(uuid).isPresent());

        service.set(playerId, Optional.empty());
        registry.refreshAll();
        assertFalse(registry.isMuted(uuid));
    }

    @Test
    void refreshDiscoversMuteAppliedAfterUnmutedPlayerWasTracked() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();
        long playerId = 12L;

        registry.trackIfMuted(uuid, playerId);
        assertFalse(registry.isMuted(uuid));

        service.set(playerId, Optional.of(
                sanction(2L, "new mute", Instant.now(), Instant.now().plusSeconds(120))
        ));
        registry.refreshAll();

        assertTrue(registry.isMuted(uuid));
    }

    @Test
    void expiredMuteIsRemovedOnCheck() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();

        service.set(13L, Optional.of(sanction(3L, "reason", Instant.now(), Instant.now().minusSeconds(1))));
        registry.trackIfMuted(uuid, 13L);

        assertFalse(registry.isMuted(uuid));
        assertFalse(registry.get(uuid).isPresent());
    }

    @Test
    void removedPlayerIsNotReintroducedByLaterRefresh() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();
        long playerId = 14L;

        registry.trackIfMuted(uuid, playerId);
        registry.remove(uuid);
        service.set(playerId, Optional.of(
                sanction(4L, "late mute", Instant.now(), Instant.now().plusSeconds(120))
        ));

        registry.refreshAll();

        assertFalse(registry.isMuted(uuid));
    }

    @Test
    void disconnectDuringLookupCannotRestoreStaleMute() throws Exception {
        SanctionEntity activeMute = sanction(
                5L,
                "in flight",
                Instant.now(),
                Instant.now().plusSeconds(120)
        );
        BlockingSanctionsDataService service = new BlockingSanctionsDataService(activeMute);
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();

        CompletableFuture<Void> lookup = CompletableFuture.runAsync(
                () -> registry.trackIfMuted(uuid, 16L)
        );
        assertTrue(service.queryStarted.await(5L, TimeUnit.SECONDS));

        registry.remove(uuid);
        service.releaseQuery.countDown();
        lookup.get(5L, TimeUnit.SECONDS);

        assertFalse(registry.isMuted(uuid));
    }

    @Test
    void invalidCanonicalIdentityIsIgnored() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);

        registry.trackIfMuted(UUID.randomUUID(), 0L);
        registry.trackIfMuted(null, 15L);

        assertTrue(service.queriedPlayerIds.isEmpty());
    }

    @Test
    void notifyThrottlingBlocksRapidRepeatCalls() {
        MuteRegistry registry = new MuteRegistry(new FakeSanctionsDataService());
        UUID uuid = UUID.randomUUID();

        assertTrue(registry.shouldNotify(uuid));
        assertFalse(registry.shouldNotify(uuid));

        registry.remove(uuid);
        assertTrue(registry.shouldNotify(uuid));
    }

    private static SanctionEntity sanction(Long id, String reason, Instant createdAt, Instant expiresAt) {
        SanctionEntity s = new SanctionEntity();
        s.setReason(reason);
        s.setCreatedAt(createdAt);
        s.setExpiresAt(expiresAt);
        setField(s, "id", id);
        return s;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class FakeSanctionsDataService extends SanctionsDataService {
        private final Map<Long, Optional<SanctionEntity>> byPlayerId = new HashMap<>();
        private final java.util.List<Long> queriedPlayerIds = new java.util.ArrayList<>();

        private FakeSanctionsDataService() {
            super(null);
        }

        void set(long playerId, Optional<SanctionEntity> sanction) {
            byPlayerId.put(playerId, sanction);
        }

        @Override
        public Optional<SanctionEntity> findActiveMuteByPlayerId(long playerId) {
            queriedPlayerIds.add(playerId);
            return byPlayerId.getOrDefault(playerId, Optional.empty());
        }
    }

    private static final class BlockingSanctionsDataService extends SanctionsDataService {
        private final SanctionEntity result;
        private final CountDownLatch queryStarted = new CountDownLatch(1);
        private final CountDownLatch releaseQuery = new CountDownLatch(1);

        private BlockingSanctionsDataService(SanctionEntity result) {
            super(null);
            this.result = result;
        }

        @Override
        public Optional<SanctionEntity> findActiveMuteByPlayerId(long playerId) {
            queryStarted.countDown();
            try {
                if (!releaseQuery.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release mute lookup.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Mute lookup was interrupted.", exception);
            }
            return Optional.of(result);
        }
    }
}
