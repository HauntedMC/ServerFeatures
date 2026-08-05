package nl.hauntedmc.serverfeatures.features.glow.internal;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.api.ui.hud.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles enabling/disabling glow effects and animating them if needed.
 * Tracks per-player active effect and drives animations.
 */
public class GlowHandler {

    private final Glow feature;

    private final Map<UUID, TrackedEffect> activeEffects = new ConcurrentHashMap<>();
    private final Map<UUID, TrackedEffect> animatedEffects = new ConcurrentHashMap<>();

    public GlowHandler(Glow feature) {
        this.feature = feature;
        // Drive animations once per second.
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(this::tick, BukkitTime.seconds(1));
    }

    /**
     * Set an effect for a player (permission-checked) and persist to DB.
     */
    public boolean setGlow(Player player, GlowEffect effect) {
        if (!player.hasPermission("serverfeatures.feature.glow.use")) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission")
                            .forAudience(player)
                            .build()
            );
            return false;
        }
        if (!player.hasPermission(effect.permission())) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission_reason")
                            .with("reason", feature.getLocalizationHandler()
                                    .getMessage("glow.menu.color.lore.locked")
                                    .forAudience(player)
                                    .build())
                            .forAudience(player)
                            .build()
            );
            return false;
        }

        applyNow(player, effect);
        trackEffect(player, effect);

        // Persist enabled+effect
        feature.getGlowStateService().saveGlowState(player, Optional.of(effect));
        return true;
    }

    /**
     * Restore a glow from DB without re-persisting (DB already reflects this).
     * Respects current permissions; silently skips if not allowed.
     */
    public void restoreGlow(Player player, GlowEffect effect) {
        if (!player.hasPermission("serverfeatures.feature.glow.use")) return;
        if (!player.hasPermission(effect.permission())) return;

        applyNow(player, effect);
        trackEffect(player, effect);
    }

    /**
     * Remove and persist disabled state (used for /glow remove and GUI remove).
     */
    public boolean removeGlow(Player player) {
        if (!player.hasPermission("serverfeatures.feature.glow.use")) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission")
                            .forAudience(player)
                            .build()
            );
            return false;
        }

        untrackEffect(player.getUniqueId());
        ScoreboardManager.removeGlow(player);

        // Persist disabled
        feature.getGlowStateService().saveGlowState(player, Optional.empty());
        return true;
    }

    /**
     * Remove without touching DB (used on quit to avoid overwriting persisted state).
     */
    public void removeGlowTransient(Player player) {
        untrackEffect(player.getUniqueId());
        ScoreboardManager.removeGlow(player);
    }

    /**
     * Returns whether this feature believes the player currently has any glow active.
     */
    public boolean hasActiveGlow(Player player) {
        return activeEffects.containsKey(player.getUniqueId());
    }

    /**
     * Returns the current glow effect tracked by this feature, if any.
     */
    public Optional<GlowEffect> getActiveGlow(Player player) {
        TrackedEffect tracked = activeEffects.get(player.getUniqueId());
        return tracked == null ? Optional.empty() : Optional.of(tracked.effect());
    }

    /** Drives active animated effects once per second. */
    private void tick() {
        long now = System.nanoTime();
        for (Map.Entry<UUID, TrackedEffect> entry : animatedEffects.entrySet()) {
            UUID uuid = entry.getKey();
            TrackedEffect tracked = entry.getValue();
            if (activeEffects.get(uuid) != tracked) {
                animatedEffects.remove(uuid, tracked);
                continue;
            }

            Player p = feature.getPlugin().getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                activeEffects.remove(uuid, tracked);
                animatedEffects.remove(uuid, tracked);
                continue;
            }

            NamedTextColor color = tracked.effect().colorAt(p, tracked.elapsedSeconds(now));
            ScoreboardManager.setGlow(p, color);
        }
    }

    private void trackEffect(Player player, GlowEffect effect) {
        trackEffect(player.getUniqueId(), effect, System.nanoTime());
    }

    TrackedEffect trackEffect(UUID playerId, GlowEffect effect, long activatedAtNanos) {
        return trackEffect(
                activeEffects,
                animatedEffects,
                playerId,
                effect,
                activatedAtNanos
        );
    }

    static TrackedEffect trackEffect(
            Map<UUID, TrackedEffect> active,
            Map<UUID, TrackedEffect> animated,
            UUID playerId,
            GlowEffect effect,
            long activatedAtNanos
    ) {
        TrackedEffect tracked = new TrackedEffect(effect, activatedAtNanos);
        active.put(playerId, tracked);
        if (effect.isAnimated()) {
            animated.put(playerId, tracked);
        } else {
            animated.remove(playerId);
        }
        return tracked;
    }

    private void untrackEffect(UUID playerId) {
        activeEffects.remove(playerId);
        animatedEffects.remove(playerId);
    }

    private void applyNow(Player p, GlowEffect effect) {
        NamedTextColor color = effect.colorAt(p, 0);
        ScoreboardManager.setGlow(p, color);
    }

    /** Clears runtime state and visible glow without changing persisted selections. */
    public void shutdown() {
        Set<UUID> trackedPlayers = Set.copyOf(activeEffects.keySet());
        activeEffects.clear();
        animatedEffects.clear();

        for (UUID playerId : trackedPlayers) {
            Player player = feature.getPlugin().getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                ScoreboardManager.removeGlow(player);
            } catch (RuntimeException | LinkageError failure) {
                feature.getLogger().log(Level.WARNING,
                        "Failed to remove transient glow for " + player.getName() + " during shutdown.", failure);
            }
        }
    }

    record TrackedEffect(GlowEffect effect, long activatedAtNanos) {
        TrackedEffect {
            Objects.requireNonNull(effect, "effect");
        }

        long elapsedSeconds(long nowNanos) {
            return TimeUnit.NANOSECONDS.toSeconds(Math.max(0L, nowNanos - activatedAtNanos));
        }
    }
}
