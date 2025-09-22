package nl.hauntedmc.serverfeatures.features.glow.internal;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles enabling and disabling glow effects for players.
 * Also tracks the last glow color this feature applied so that the GUI can show a
 * "current glow" status. If other systems modify glow state outside this handler,
 * the tracked value may not reflect that external change.
 */
public class GlowHandler {

    private final Glow feature;

    // Tracks the last glow color set by this feature. Missing/empty = none.
    private final Map<UUID, NamedTextColor> activeColors = new ConcurrentHashMap<>();

    public GlowHandler(Glow feature) {
        this.feature = feature;
    }

    public boolean setGlow(Player player, NamedTextColor glowColor) {
        if (!player.hasPermission("serverfeatures.feature.glow.use")) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission")
                            .forAudience(player)
                            .build()
            );
            return false;
        }

        String colorPerm = "serverfeatures.feature.glow.color." + glowColor.toString().toLowerCase();
        if (!player.hasPermission(colorPerm)) {
            player.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission_reason")
                            .withPlaceholders(Map.of("reason", "&fJe hebt deze glow kleur nog niet unlocked"))
                            .forAudience(player)
                            .build()
            );
            return false;
        }

        ScoreboardManager.setGlow(player, glowColor);
        activeColors.put(player.getUniqueId(), glowColor);
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
        activeColors.remove(player.getUniqueId());
        return true;
    }

    /** Returns whether this feature believes the player currently has glow active. */
    public boolean hasActiveGlow(Player player) {
        return activeColors.containsKey(player.getUniqueId());
    }

    /** Returns the current glow color tracked by this feature, if any. */
    public Optional<NamedTextColor> getActiveGlow(Player player) {
        return Optional.ofNullable(activeColors.get(player.getUniqueId()));
    }
}
