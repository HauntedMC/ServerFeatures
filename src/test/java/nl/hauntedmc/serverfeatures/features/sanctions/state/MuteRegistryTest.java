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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuteRegistryTest {

    @Test
    void trackRefreshAndRemoveFollowServiceState() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();

        service.set(uuid, Optional.of(sanction(1L, "reason", Instant.now(), Instant.now().plusSeconds(120))));
        registry.trackIfMuted(uuid);
        assertTrue(registry.isMuted(uuid));
        assertTrue(registry.get(uuid).isPresent());

        service.set(uuid, Optional.empty());
        registry.refreshAll();
        assertFalse(registry.isMuted(uuid));
    }

    @Test
    void expiredMuteIsRemovedOnCheck() {
        FakeSanctionsDataService service = new FakeSanctionsDataService();
        MuteRegistry registry = new MuteRegistry(service);
        UUID uuid = UUID.randomUUID();

        service.set(uuid, Optional.of(sanction(2L, "reason", Instant.now(), Instant.now().minusSeconds(1))));
        registry.trackIfMuted(uuid);

        assertFalse(registry.isMuted(uuid));
        assertFalse(registry.get(uuid).isPresent());
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
        private final Map<String, Optional<SanctionEntity>> byUuid = new HashMap<>();

        private FakeSanctionsDataService() {
            super(null);
        }

        void set(UUID uuid, Optional<SanctionEntity> sanction) {
            byUuid.put(uuid.toString(), sanction);
        }

        @Override
        public Optional<SanctionEntity> findActiveMuteByUuid(String uuid) {
            return byUuid.getOrDefault(uuid, Optional.empty());
        }
    }
}

