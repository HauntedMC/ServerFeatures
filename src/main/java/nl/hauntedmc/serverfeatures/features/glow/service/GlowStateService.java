package nl.hauntedmc.serverfeatures.features.glow.service;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import nl.hauntedmc.serverfeatures.features.glow.entity.PlayerGlowStateEntity;
import nl.hauntedmc.serverfeatures.framework.persistence.PlayerIdentityResolver;
import org.bukkit.entity.Player;
import org.hibernate.Session;

import java.util.Locale;
import java.util.Optional;

/**
 * Handles ORM persistence for player glow state.
 * Uses player_id as a scalar primary key.
 */
public class GlowStateService {

    private final Glow feature;
    private final PlayerIdentityResolver playerResolver;

    public GlowStateService(Glow feature) {
        this(feature, feature.getPlugin().getDataRegistry()
                .orElseThrow(() -> new IllegalStateException("DataRegistry is required for Glow.")));
    }

    GlowStateService(Glow feature, DataRegistryApi dataRegistry) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(dataRegistry);
    }

    GlowStateService(Glow feature, PlayerDirectory playerDirectory) {
        this.feature = feature;
        this.playerResolver = new PlayerIdentityResolver(playerDirectory);
    }

    public void saveGlowState(Player bukkitPlayer, Optional<GlowEffect> effectOpt) {
        PlayerIdentity identity = playerResolver.findActiveByUuid(bukkitPlayer.getUniqueId()).orElse(null);
        if (identity == null) {
            return;
        }
        saveGlowState(bukkitPlayer, identity, effectOpt);
    }

    public void saveGlowState(Player bukkitPlayer, PlayerIdentity identity, Optional<GlowEffect> effectOpt) {
        feature.getOrmContext().runInTransaction(session -> {
            saveGlowState(session, identity, effectOpt);
            return null;
        });
    }

    public void restoreGlowFor(Player bukkitPlayer) {
        PlayerIdentity identity = playerResolver.findActiveByUuid(bukkitPlayer.getUniqueId()).orElse(null);
        if (identity == null) {
            return;
        }
        restoreGlowFor(bukkitPlayer, identity);
    }

    public void restoreGlowFor(Player bukkitPlayer, PlayerIdentity identity) {
        feature.getOrmContext().runInTransaction(session -> {
            restoreGlowFor(session, bukkitPlayer, identity);
            return null;
        });
    }

    void saveGlowState(Session session, Player bukkitPlayer, Optional<GlowEffect> effectOpt) {
        PlayerIdentity identity = playerResolver.findActiveByUuid(bukkitPlayer.getUniqueId()).orElse(null);
        if (identity != null) {
            saveGlowState(session, identity, effectOpt);
        }
    }

    void saveGlowState(Session session, PlayerIdentity identity, Optional<GlowEffect> effectOpt) {
        if (identity == null || identity.playerId() <= 0L) {
            return;
        }

        PlayerGlowStateEntity state = session.createQuery(
                        "SELECT s FROM PlayerGlowStateEntity s WHERE s.playerId = :playerId", PlayerGlowStateEntity.class)
                .setParameter("playerId", identity.playerId())
                .uniqueResult();

        boolean isNew = false;
        if (state == null) {
            state = new PlayerGlowStateEntity();
            state.setPlayerId(identity.playerId());
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
        }
    }

    void restoreGlowFor(Session session, Player bukkitPlayer) {
        PlayerIdentity identity = playerResolver.findActiveByUuid(bukkitPlayer.getUniqueId()).orElse(null);
        if (identity != null) {
            restoreGlowFor(session, bukkitPlayer, identity);
        }
    }

    void restoreGlowFor(Session session, Player bukkitPlayer, PlayerIdentity identity) {
        if (identity == null || identity.playerId() <= 0L) {
            return;
        }

        PlayerGlowStateEntity state = session.createQuery(
                        "SELECT s FROM PlayerGlowStateEntity s WHERE s.playerId = :playerId", PlayerGlowStateEntity.class)
                .setParameter("playerId", identity.playerId())
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
}
