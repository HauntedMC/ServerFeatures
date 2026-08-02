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
        orm.runInTransaction(session -> {
            PlayerAutoPickupSettingEntity setting = session.find(PlayerAutoPickupSettingEntity.class, playerId);
            if (setting == null) {
                setting = new PlayerAutoPickupSettingEntity();
                setting.setPlayerId(playerId);
                session.persist(setting);
            }
            setting.setEnabled(enabled);
            setting.setUpdatedAt(System.currentTimeMillis());
            return null;
        });
    }
}
