package nl.hauntedmc.serverfeatures.features.sanctions.state;

import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.serverfeatures.features.sanctions.service.SanctionsDataService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks muted players on the server and refreshes their state periodically.
 * Uses a single global sweeper scheduled by the feature (interval from config).
 */
public class MuteRegistry {

    /**
     * @param expiresAt null for permanent
     */
    public record MuteState(long sanctionId, String reason, Instant createdAt, Instant expiresAt) {

        public boolean isPermanent() {
            return expiresAt == null;
        }

        public boolean isExpiredNow() {
                return expiresAt != null && expiresAt.isBefore(Instant.now());
            }
        }

    private final SanctionsDataService service;

    // Active muted players on this server
    private final Map<UUID, MuteState> muted = new ConcurrentHashMap<>();
    // Small cooldown to avoid spam if needed (per-player chat notify throttle)
    private final Map<UUID, Long> lastNotify = new ConcurrentHashMap<>();

    public MuteRegistry(SanctionsDataService service) {
        this.service = service;
    }

    public void trackIfMuted(UUID uuid) {
        service.findActiveMuteByUuid(uuid.toString()).ifPresentOrElse(s -> muted.put(uuid, toState(s)), () -> muted.remove(uuid));
    }

    public boolean isMuted(UUID uuid) {
        MuteState ms = muted.get(uuid);
        if (ms == null) return false;
        if (ms.isExpiredNow()) {
            muted.remove(uuid);
            return false;
        }
        return true;
    }

    public Optional<MuteState> get(UUID uuid) {
        return Optional.ofNullable(muted.get(uuid));
    }

    /** Refresh all tracked mutes from DB (called by the global sweeper). */
    public void refreshAll() {
        if (muted.isEmpty()) return;
        for (UUID uuid : new ArrayList<>(muted.keySet())) {
            service.findActiveMuteByUuid(uuid.toString()).ifPresentOrElse(s -> muted.put(uuid, toState(s)), () -> muted.remove(uuid));
        }
    }

    public void clear() {
        muted.clear();
        lastNotify.clear();
    }

    public void remove(UUID uuid) {
        muted.remove(uuid);
        lastNotify.remove(uuid);
    }

    public boolean shouldNotify(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastNotify.getOrDefault(uuid, 0L);
        if (now - last >= 1500L) { // 1.5s throttle
            lastNotify.put(uuid, now);
            return true;
        }
        return false;
    }

    private MuteState toState(SanctionEntity s) {
        return new MuteState(
                s.getId(),
                service.sanitize(s.getReason()),
                s.getCreatedAt(),
                s.getExpiresAt()
        );
    }
}
