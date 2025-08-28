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

    /** Query the DB for an active MUTE for a given UUID. Returns the newest active mute if present. */
    public Optional<SanctionEntity> findActiveMuteByUuid(String uuid) {
        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "select s from SanctionEntity s " +
                                        "join s.targetPlayer p " +
                                        "where s.active = true and s.type = :t and p.uuid = :uuid " +
                                        "order by s.createdAt desc", SanctionEntity.class)
                        .setParameter("t", SanctionType.MUTE)
                        .setParameter("uuid", uuid)
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

    /** Deactivate a sanction by ID, if currently active. */
    public void deactivateById(Long id) {
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = session.get(SanctionEntity.class, id);
            if (s != null && s.isActive()) {
                s.setActive(false);
            }
            return null;
        });
    }

    /** Remaining duration string for a temporary mute. */
    public String remaining(Instant now, Instant expiresAt) {
        if (expiresAt == null) return "permanent";
        long seconds = Math.max(0, expiresAt.getEpochSecond() - now.getEpochSecond());
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0m" : out;
    }


    /** Null-safe sanitize reason for display (simple trim). */
    public String sanitize(String reason) {
        if (reason == null) return "-";
        String r = reason.trim();
        if (r.isBlank()) return "-";
        // Optional: strip control chars
        return r.replaceAll("\\p{Cntrl}", "").substring(0, Math.min(512, r.length()));
    }
}
