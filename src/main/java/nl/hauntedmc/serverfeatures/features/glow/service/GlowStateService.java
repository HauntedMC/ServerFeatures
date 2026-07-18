package nl.hauntedmc.serverfeatures.features.glow.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import nl.hauntedmc.serverfeatures.features.glow.entity.PlayerGlowStateEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerEntityResolver;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.Locale;
import java.util.Optional;

/**
 * Handles ORM persistence for player glow state.
 * Uses player_id as the primary key via shared PK with PlayerEntity.
 */
public class GlowStateService {

    private final Glow feature;
    private final PlayerEntityResolver playerResolver;

    public GlowStateService(Glow feature) {
        this.feature = feature;
        this.playerResolver = new PlayerEntityResolver(
                feature.getPlugin().getDataRegistry()
                        .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Glow."))
        );
    }

    /**
     * Save (upsert) the player's glow state. If effect is empty -> disabled.
     */
    public void saveGlowState(Player bukkitPlayer, Optional<GlowEffect> effectOpt) {
        feature.getOrmContext().runInTransaction(session -> {
            saveGlowState(session, bukkitPlayer, effectOpt);
            return null;
        });
    }

    /**
     * On join: restore glow if DB says enabled and effect is known & permitted.
     * Silently skips if effect no longer exists or permissions are missing.
     */
    public void restoreGlowFor(Player bukkitPlayer) {
        feature.getOrmContext().runInTransaction(session -> {
            restoreGlowFor(session, bukkitPlayer);
            return null;
        });
    }

    void saveGlowState(Session session, Player bukkitPlayer, Optional<GlowEffect> effectOpt) {
        PlayerEntity playerEntity = resolveExistingPlayerEntity(
                session,
                bukkitPlayer.getUniqueId().toString(),
                bukkitPlayer.getName()
        );

        if (playerEntity == null) {
            return;
        }

        PlayerGlowStateEntity state = session.createQuery(
                        "SELECT s FROM PlayerGlowStateEntity s WHERE s.player = :player", PlayerGlowStateEntity.class)
                .setParameter("player", playerEntity)
                .uniqueResult();

        boolean isNew = false;
        if (state == null) {
            state = new PlayerGlowStateEntity();
            state.setPlayer(playerEntity);
            isNew = true;
        }

        if (effectOpt.isPresent()) {
            GlowEffect effect = effectOpt.get();
            state.setEnabled(true);
            state.setEffectId(effect.id().toLowerCase(Locale.ROOT));
        } else {
            state.setEnabled(false);
            state.setEffectId(null);
        }

        if (isNew) {
            session.persist(state);
        } else {
            session.merge(state);
        }
    }

    void restoreGlowFor(Session session, Player bukkitPlayer) {
        PlayerEntity playerEntity = resolveExistingPlayerEntity(
                session,
                bukkitPlayer.getUniqueId().toString(),
                bukkitPlayer.getName()
        );

        if (playerEntity == null) {
            return;
        }

        PlayerGlowStateEntity state = session.createQuery(
                        "SELECT s FROM PlayerGlowStateEntity s WHERE s.player = :player", PlayerGlowStateEntity.class)
                .setParameter("player", playerEntity)
                .uniqueResult();

        if (state == null || !state.isEnabled()) {
            return;
        }

        String effectId = state.getEffectId();
        if (effectId == null || effectId.isBlank()) {
            return;
        }

        feature.getGlowRegistry().find(effectId).ifPresent(effect ->
                feature.getGlowHandler().restoreGlow(bukkitPlayer, effect)
        );
    }

    private PlayerEntity resolveExistingPlayerEntity(Session session, String uuid, String username) {
        return playerResolver.resolveManaged(session, java.util.UUID.fromString(uuid), username);
    }
}
