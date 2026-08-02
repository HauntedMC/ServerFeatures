package nl.hauntedmc.serverfeatures.features.autopickup.persistence;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Objects;
import java.util.Optional;

public final class AutoPickupPreferenceRepository {

    private final ORMContext orm;

    public AutoPickupPreferenceRepository(ORMContext orm) {
        this.orm = Objects.requireNonNull(orm, "orm");
    }

    public Optional<Boolean> load(long playerId) {
        if (playerId <= 0L) {
            return Optional.empty();
        }
        return orm.runInTransaction(session -> session.createSelectionQuery(
                        "SELECT setting.enabled FROM PlayerAutoPickupSettingEntity setting "
                                + "WHERE setting.playerId = :playerId",
                        Boolean.class
                )
                .setParameter("playerId", playerId)
                .uniqueResultOptional());
    }

    public void upsert(long playerId, boolean enabled) {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be positive");
        }
        long updatedAt = System.currentTimeMillis();
        orm.runInTransaction(session -> {
            session.createNativeMutationQuery(
                            "INSERT INTO player_auto_pickup_settings (player_id, enabled, updated_at) "
                                    + "VALUES (:playerId, :enabled, :updatedAt) "
                                    + "ON DUPLICATE KEY UPDATE enabled = :enabled, updated_at = :updatedAt"
                    )
                    .setParameter("playerId", playerId)
                    .setParameter("enabled", enabled)
                    .setParameter("updatedAt", updatedAt)
                    .executeUpdate();
            return null;
        });
    }
}
