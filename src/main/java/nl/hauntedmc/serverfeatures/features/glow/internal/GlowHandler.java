package nl.hauntedmc.serverfeatures.features.glow.internal;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * Handles enabling and disabling glow effects for players.
 */
public class GlowHandler {

    private final Glow feature;

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

        // Delegate to ScoreboardManager
        ScoreboardManager.setGlow(player, glowColor);
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
        return true;
    }
}
