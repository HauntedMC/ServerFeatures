package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.serverfeatures.features.autopickup.entity.PlayerAutoPickupSettingEntity;

import java.util.Objects;
import java.util.Optional;

public final class AutoPickupPreferenceRepository {

    private final ORMContext orm;

    public AutoPickupPreferenceRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
    }

    public Optional<StoredPreference> load(long playerId) {
        if (playerId <= 0L) {
            return Optional.empty();
        }
        return orm.runInTransaction(session -> session.createSelectionQuery(
                        "FROM PlayerAutoPickupSettingEntity setting WHERE setting.playerId = :playerId",
                        PlayerAutoPickupSettingEntity.class
                )
                .setParameter("playerId", playerId)
                .uniqueResultOptional()
                .map(AutoPickupPreferenceRepository::storedPreference));
    }

    public StoredPreference upsert(long playerId, boolean enabled, long writeRevision) {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be positive");
        }
        if (writeRevision <= 0L) {
            throw new IllegalArgumentException("writeRevision must be positive");
        }
        long updatedAt = System.currentTimeMillis();
        return orm.runInTransaction(session -> {
            session.createNativeMutationQuery(
                            "INSERT INTO player_auto_pickup_settings "
                                    + "(player_id, enabled, updated_at, write_revision) "
                                    + "VALUES (:playerId, :enabled, :updatedAt, :writeRevision) "
                                    + "ON DUPLICATE KEY UPDATE "
                                    + "enabled = IF(:writeRevision > write_revision, :enabled, enabled), "
                                    + "updated_at = IF(:writeRevision > write_revision, :updatedAt, updated_at), "
                                    + "write_revision = GREATEST(write_revision, :writeRevision)"
                    )
                    .setParameter("playerId", playerId)
                    .setParameter("enabled", enabled)
                    .setParameter("updatedAt", updatedAt)
                    .setParameter("writeRevision", writeRevision)
                    .executeUpdate();

            // Native mutations bypass Hibernate's managed entity state. Clear before reading the winner.
            session.clear();
            PlayerAutoPickupSettingEntity stored = session.createSelectionQuery(
                            "FROM PlayerAutoPickupSettingEntity setting WHERE setting.playerId = :playerId",
                            PlayerAutoPickupSettingEntity.class
                    )
                    .setParameter("playerId", playerId)
                    .getSingleResult();
            return storedPreference(stored);
        });
    }

    private static StoredPreference storedPreference(PlayerAutoPickupSettingEntity entity) {
        return new StoredPreference(entity.isEnabled(), entity.getWriteRevision());
    }

    public record StoredPreference(boolean enabled, long writeRevision) {
        public StoredPreference {
            if (writeRevision < 0L) {
                throw new IllegalArgumentException("writeRevision cannot be negative");
            }
        }
    }
}
