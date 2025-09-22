package nl.hauntedmc.serverfeatures.features.glow.internal;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowEffect;
import nl.hauntedmc.serverfeatures.features.glow.effect.GlowRegistry;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles enabling/disabling glow effects and animating them if needed.
 * Tracks per-player active effect and start time to drive animations.
 */
public class GlowHandler {

    private final Glow feature;

    private final Map<UUID, GlowEffect> activeEffects = new ConcurrentHashMap<>();

    public GlowHandler(Glow feature, GlowRegistry registry) {
        this.feature = feature;
        // Drive animations once per second.
        feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(this::tick, BukkitTime.seconds(1));
    }

    /**
     * Set an effect for a player (permission-checked).
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
                            .withPlaceholders(Map.of("reason", "&fJe hebt deze glow nog niet unlocked"))
                            .forAudience(player)
                            .build()
            );
            return false;
        }

        activeEffects.put(player.getUniqueId(), effect);

        // Apply immediately
        applyNow(player, effect);

        return true;
    }

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

        ScoreboardManager.removeGlow(player);
        activeEffects.remove(player.getUniqueId());
        return true;
    }

    /** Returns whether this feature believes the player currently has any glow active. */
    public boolean hasActiveGlow(Player player) {
        return activeEffects.containsKey(player.getUniqueId());
    }

    /** Returns the current glow effect tracked by this feature, if any. */
    public Optional<GlowEffect> getActiveGlow(Player player) {
        GlowEffect a = activeEffects.get(player.getUniqueId());
        return a == null ? Optional.empty() : Optional.of(a);
    }

    /** Drive animations and keep static effects consistent (cheap). */
    private void tick() {
        long now = Instant.now().getEpochSecond();
        for (Map.Entry<UUID, GlowEffect> entry : activeEffects.entrySet()) {
            GlowEffect effect = entry.getValue();
            if (effect == null) {
                return;
            }

            if (!effect.isAnimated()) {
                return;
            }
            UUID uuid = entry.getKey();
            Player p = feature.getPlugin().getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            long elapsed = Math.max(0, now);
            NamedTextColor color = effect.colorAt(p, elapsed);
            ScoreboardManager.setGlow(p, color);
        }
    }

    private void applyNow(Player p, GlowEffect effect) {
        NamedTextColor color = effect.colorAt(p, 0);
        ScoreboardManager.setGlow(p, color);
    }

}
