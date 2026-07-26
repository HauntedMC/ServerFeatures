package nl.hauntedmc.serverfeatures.features.sanctions.service;

import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType;
import nl.hauntedmc.serverfeatures.features.sanctions.Sanctions;

import java.time.Instant;
import java.util.Optional;

public class SanctionsDataService {

    private final Sanctions feature;

    public SanctionsDataService(Sanctions feature) {
        this.feature = feature;
    }

    /**
     * Queries an active mute by the canonical player id already resolved by DataRegistry.
     */
    public Optional<SanctionEntity> findActiveMuteByPlayerId(long playerId) {
        if (playerId <= 0L) {
            return Optional.empty();
        }
        if (feature == null) {
            throw new IllegalStateException("SanctionsDataService requires a Sanctions feature to query mute data.");
        }
        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "select s from SanctionEntity s " +
                                        "where s.active = true and s.type = :t and s.targetPlayerId = :playerId " +
                                        "order by s.createdAt desc", SanctionEntity.class)
                        .setParameter("t", SanctionType.MUTE)
                        .setParameter("playerId", playerId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        ).map(s -> {
            // Fast-path: if mute expired, deactivate it immediately and return empty
            if (!s.isPermanent() && s.getExpiresAt() != null && s.getExpiresAt().isBefore(Instant.now())) {
                deactivateById(s.getId());
                return null;
            }
            return s;
        });
    }

    /**
     * Deactivate a sanction by ID, if currently active.
     */
    public void deactivateById(Long id) {
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = session.find(SanctionEntity.class, id);
            if (s != null && s.isActive()) {
                s.setActive(false);
            }
            return null;
        });
    }

    /**
     * Remaining duration string for a temporary mute.
     */
    public String remaining(Instant now, Instant expiresAt) {
        if (expiresAt == null) return "permanent";
        long seconds = Math.max(0, expiresAt.getEpochSecond() - now.getEpochSecond());
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0m" : out;
    }


    /**
     * Null-safe sanitize reason for display (simple trim).
     */
    public String sanitize(String reason) {
        if (reason == null) return "-";
        String r = reason.trim();
        if (r.isBlank()) return "-";
        // Optional: strip control chars
        String cleaned = r.replaceAll("\\p{Cntrl}", "");
        if (cleaned.isBlank()) return "-";
        return cleaned.substring(0, Math.min(512, cleaned.length()));
    }
}
