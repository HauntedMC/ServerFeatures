package nl.hauntedmc.serverfeatures.features.glow.internal;

import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.common.scoreboard.ScoreboardManager;
import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.entity.Player;

/**
 * Handles enabling and disabling glow effects for players.
 * Includes permission checks and scoreboard updates.
 */
public class GlowHandler {

    private final Glow feature;

    public GlowHandler(Glow feature) {
        this.feature = feature;
    }

    /**
     * Enables glow for a player using the provided color.
     *
     * @param player    The player to glow
     * @param glowColor The glow color
     * @return true if the glow was successfully set; false otherwise
     */
    public boolean setGlow(Player player, NamedTextColor glowColor) {
        if (!player.hasPermission("serverfeatures.feature.glow.use")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
            return false;
        }
        String colorPerm = "serverfeatures.feature.glow.color." + glowColor.toString().toLowerCase();
        if (!player.hasPermission(colorPerm)) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
            return false;
        }
        player.setGlowing(true);
        if (!ScoreboardManager.hasValidTeam(player)) {
            return false;
        }
        ScoreboardManager.setTeamColor(player, glowColor);
        return true;
    }

    /**
     * Disables the glow effect for a player.
     *
     * @param player the player to remove glow from
     * @return true if the glow was successfully removed; false otherwise
     */
    public boolean removeGlow(Player player) {
        if (!player.hasPermission("serverfeatures.feature.glow.use")) {
            player.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission"));
            return false;
        }
        player.setGlowing(false);
        if (!ScoreboardManager.hasValidTeam(player)) {
            return false;
        }
        ScoreboardManager.setTeamColor(player, NamedTextColor.GRAY);
        return true;
    }
}
